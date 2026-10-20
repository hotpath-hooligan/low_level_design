package model;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Seat state for one event, guarded per seat rather than per inventory: every
 * transition touches exactly one seat and runs as a single computeIfPresent over
 * that seat's entry, so the map holds the bin's lock for the call and deciding
 * and writing are atomic without a lock of our own. Nothing here spans two seats.
 *
 * <p>Every result is read off the state the transition left behind, which works
 * because HELD and BOOKED carry a holdId and a bookingId that are never reused.
 * AVAILABLE carries nothing, so releasing a seat and finding it already released
 * are indistinguishable afterwards -- the two are reported the same way, which
 * is what makes release and cancel idempotent rather than a limitation worked
 * around. Distinguishing them would mean recording who acted, not what is true.
 *
 * <p>This guards a JVM map, so it is correct for one process and nothing more.
 * Across servers the seat is a database row and a hold is a conditional UPDATE
 * checked by affected-row count.
 */
public final class SeatInventory {
    private final Map<String, Seat> seatsById;
    private final Map<String, SeatState> seatStates;
    private final Clock clock;
    private final Duration holdDuration;

    public SeatInventory(
            Collection<Seat> seats,
            Clock clock,
            Duration holdDuration
    ) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.holdDuration = Objects.requireNonNull(
                holdDuration,
                "holdDuration must not be null"
        );
        if (holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("holdDuration must be positive");
        }
        Map<String, Seat> byId = new HashMap<>();
        this.seatStates = new ConcurrentHashMap<>();
        for (Seat seat : seats) {
            byId.put(seat.id(), seat);
            seatStates.put(seat.id(), SeatState.available());
        }
        this.seatsById = Map.copyOf(byId);
    }

    /**
     * A per-seat snapshot, not a whole-map one: each entry is individually
     * consistent, but entries are read as the iteration reaches them. Every
     * transition here touches a single seat, so there is no cross-seat invariant
     * a reader could catch half-applied -- a map read cannot tear something that
     * was never written together.
     */
    public Map<String, SeatStatus> getSeatMap() {
        Instant now = clock.instant();
        Map<String, SeatStatus> snapshot = new HashMap<>();

        seatStates.forEach((seatId, state) -> snapshot.put(
                seatId,
                state.isExpired(now) ? SeatStatus.AVAILABLE : state.status()
        ));

        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Seats free right now, counting holds that have lapsed but not been swept.
     * Weakly consistent in the same way as {@link #getSeatMap()}.
     */
    public Set<String> getAvailableSeats() {
        Instant now = clock.instant();
        Set<String> available = new HashSet<>();

        seatStates.forEach((seatId, state) -> {
            if (state.isAvailable(now)) {
                available.add(seatId);
            }
        });

        return Collections.unmodifiableSet(available);
    }

    Seat getSeatById(String seatId) {
        return seatsById.get(seatId);
    }

    public SeatResult holdSeat(String userId, String seatId) {
        Instant now = clock.instant();

        // Racy fast path: on a hot seat almost every caller loses, and minting a
        // UUID for each of them dominates the contended cost. computeIfPresent
        // re-checks below, so a stale read here can only cost a retry.
        SeatState observed = seatStates.get(seatId);
        if (observed == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        if (!observed.isAvailable(now)) {
            return SeatResult.of(SeatResult.Status.SEAT_UNAVAILABLE);
        }

        String holdId = UUID.randomUUID().toString();
        Instant expiresAt = now.plus(holdDuration);

        SeatState current = seatStates.computeIfPresent(
                seatId,
                (key, state) -> state.isAvailable(now)
                        ? SeatState.held(userId, holdId, expiresAt)
                        : state
        );

        if (current == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        // The holdId was minted for this call, so finding it on the seat proves
        // the transition above was ours.
        if (current.isHeldBy(userId, holdId)) {
            return SeatResult.held(holdId);
        }
        return SeatResult.of(SeatResult.Status.SEAT_UNAVAILABLE);
    }

    public SeatResult bookSeat(
            String userId,
            String seatId,
            String holdId,
            String bookingId
    ) {
        Instant now = clock.instant();

        SeatState current = seatStates.computeIfPresent(seatId, (key, state) -> {
            if (state.status() == SeatStatus.BOOKED) {
                return state;
            }
            if (state.isExpired(now)) {
                return SeatState.available();
            }
            if (!state.isHeldBy(userId, holdId)) {
                return state;
            }
            return SeatState.booked(userId, holdId, bookingId);
        });

        if (current == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        // Each branch above lands on a state only it can produce, so the result
        // reads off the state and needs no channel of its own. Retrying a
        // booking that already succeeded is not a failure, and returns the
        // bookingId the winner wrote.
        if (current.wasBookedFrom(userId, holdId)) {
            return SeatResult.booked(current.bookingId());
        }
        if (current.status() == SeatStatus.BOOKED) {
            return SeatResult.of(SeatResult.Status.SEAT_UNAVAILABLE);
        }
        return SeatResult.of(SeatResult.Status.HOLD_INVALID);
    }

    public SeatResult releaseHold(String userId, String seatId, String holdId) {
        Instant now = clock.instant();

        // A lapsed hold is left where it is rather than reclaimed here: expiry
        // is lazy everywhere else, and leaving it keeps AVAILABLE meaning
        // exactly one thing -- that this call, or an earlier one, released it.
        SeatState current = seatStates.computeIfPresent(
                seatId,
                (key, state) -> state.isExpired(now)
                        || !state.isHeldBy(userId, holdId)
                        ? state
                        : SeatState.available()
        );

        if (current == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        return current.status() == SeatStatus.AVAILABLE
                ? SeatResult.of(SeatResult.Status.SUCCESS)
                : SeatResult.of(SeatResult.Status.HOLD_INVALID);
    }

    public SeatResult cancelBooking(String userId, String seatId, String bookingId) {
        SeatState current = seatStates.computeIfPresent(seatId, (key, state) ->
                // Refusing a bookingId the seat no longer carries is what makes
                // a replayed cancel safe now that liveness is read off the seat.
                state.status() != SeatStatus.BOOKED
                        || !state.hasBookingId(bookingId)
                        || !state.isOwnedBy(userId)
                        ? state
                        : SeatState.available());

        if (current == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        return current.status() == SeatStatus.AVAILABLE
                ? SeatResult.of(SeatResult.Status.SUCCESS)
                : SeatResult.of(SeatResult.Status.NOT_FOUND);
    }

    /**
     * Reclaims lapsed holds so their state stops being retained. Expiry is
     * already lazy, so this is a memory sweep, not a correctness step.
     */
    public int cleanExpiredHolds() {
        Instant now = clock.instant();
        SeatState availableState = SeatState.available();
        int cleaned = 0;

        for (Map.Entry<String, SeatState> entry : seatStates.entrySet()) {
            // The CAS cannot clobber a concurrent re-hold: every hold carries a
            // fresh holdId, so a newer state never compares equal to this one.
            // That also makes overlapping sweeps safe, so this needs no lock.
            if (entry.getValue().isExpired(now)
                    && seatStates.replace(entry.getKey(), entry.getValue(), availableState)) {
                cleaned++;
            }
        }

        return cleaned;
    }

    // Internal representation of a seat's state. status is the external representation.
    private record SeatState(
            SeatStatus status,
            String userId,
            String holdId,
            String bookingId,
            Instant expiresAt
    ) {

        private static SeatState available() {
            return new SeatState(SeatStatus.AVAILABLE, null, null, null, null);
        }

        private static SeatState held(
                String userId,
                String holdId,
                Instant expiresAt
        ) {
            return new SeatState(
                    SeatStatus.HELD,
                    userId,
                    holdId,
                    null,
                    expiresAt
            );
        }

        private static SeatState booked(
                String userId,
                String holdId,
                String bookingId
        ) {
            return new SeatState(
                    SeatStatus.BOOKED,
                    userId,
                    holdId,
                    bookingId,
                    null
            );
        }

        private boolean isAvailable(Instant now) {
            return status == SeatStatus.AVAILABLE || isExpired(now);
        }

        private boolean isHeldBy(String requestedUserId, String requestedHoldId) {
            return status == SeatStatus.HELD
                    && isOwnedBy(requestedUserId)
                    && hasHoldId(requestedHoldId);
        }

        private boolean isOwnedBy(String requestedUserId) {
            return Objects.equals(userId, requestedUserId);
        }

        private boolean hasHoldId(String requestedHoldId) {
            return Objects.equals(holdId, requestedHoldId);
        }

        private boolean hasBookingId(String requestedBookingId) {
            return Objects.equals(bookingId, requestedBookingId);
        }

        private boolean wasBookedFrom(
                String requestedUserId,
                String requestedHoldId
        ) {
            return status == SeatStatus.BOOKED
                    && isOwnedBy(requestedUserId)
                    && hasHoldId(requestedHoldId);
        }

        private boolean isExpired(Instant now) {
            return status == SeatStatus.HELD && !now.isBefore(expiresAt);
        }
    }
}
