package model;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class Event {
    private final String id;
    private final String name;
    private final Instant startsAt;
    private final Instant endsAt;
    private final SeatInventory seatInventory;
    private final Clock clock;

    public Event(
            String id,
            String name,
            Instant startsAt,
            Instant endsAt,
            SeatInventory seatInventory,
            Clock clock
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        this.startsAt = Objects.requireNonNull(startsAt, "startsAt must not be null");
        this.seatInventory = Objects.requireNonNull(seatInventory);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.name = name;
        this.endsAt = endsAt;
    }

    public String getId() {
        return id;
    }

    /** Seats are frozen once the show begins. */
    public boolean hasStarted() {
        return !clock.instant().isBefore(startsAt);
    }

    public Map<String, SeatStatus> getSeatMap() {
        return seatInventory.getSeatMap();
    }

    public Set<String> getAvailableSeats() {
        return seatInventory.getAvailableSeats();
    }

    public Seat getSeatById(String seatId) {
        return seatInventory.getSeatById(seatId);
    }

    public SeatResult holdSeat(String userId, String seatId) {
        return hasStarted()
                ? SeatResult.of(SeatResult.Status.EVENT_STARTED)
                : seatInventory.holdSeat(userId, seatId);
    }

    public SeatResult confirmHold(
            String userId,
            String seatId,
            String holdId,
            String bookingId
    ) {
        return hasStarted()
                ? SeatResult.of(SeatResult.Status.EVENT_STARTED)
                : seatInventory.bookSeat(userId, seatId, holdId, bookingId);
    }

    public SeatResult releaseHold(
            String userId,
            String seatId,
            String holdId
    ) {
        return seatInventory.releaseHold(userId, seatId, holdId);
    }

    public SeatResult cancelBookedSeat(
            String userId,
            String seatId,
            String bookingId
    ) {
        return seatInventory.cancelBooking(userId, seatId, bookingId);
    }

    public int cleanExpiredHolds() {
        return seatInventory.cleanExpiredHolds();
    }
}
