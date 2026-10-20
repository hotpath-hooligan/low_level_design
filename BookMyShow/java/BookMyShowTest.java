import model.Event;
import model.SeatResult;
import model.SeatStatus;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public final class BookMyShowTest implements AutoCloseable {
    private static final Duration HOLD = Duration.ofMinutes(5);
    private static final int USERS = 20;
    private static final int ROUNDS = 200;

    private final MutableClock clock = new MutableClock();
    private final BookMyShow app = new BookMyShow(clock);
    private final String eventId = app.addEvent("Show", Instant.EPOCH.plusSeconds(3_600),
            Instant.EPOCH.plusSeconds(7_200), HOLD, List.of("A1")).getId();

    public static void main(String[] args) throws Exception {
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.concurrentHoldsElectOneWinner();
        }
        // Repeated: the publish-before-claim window is a few instructions wide.
        for (int round = 0; round < ROUNDS; round++) {
            try (BookMyShowTest test = new BookMyShowTest()) {
                test.bookingIsHoldBoundAndIdempotent();
            }
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.releasingIsHoldBoundAndIdempotent();
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.releasingALapsedHoldIsRefused();
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.cancellingFreesTheSeat();
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.concurrentCancelsFreeTheSeatOnce();
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.staleCancelCannotFreeSomeoneElsesSeat();
        }
        try (BookMyShowTest test = new BookMyShowTest()) {
            test.cleanerReclaimsExpiredHolds();
        }
        System.out.println("All tests passed");
    }

    private void concurrentHoldsElectOneWinner() throws Exception {
        List<SeatResult> holds = race(i -> app.holdSeat("user" + i, eventId, "A1"));
        equal(1L, holds.stream().filter(SeatResult::succeeded).count());
        equal(SeatStatus.HELD, seatStatus());
    }

    private void bookingIsHoldBoundAndIdempotent() throws Exception {
        String holdId = app.holdSeat("alice", eventId, "A1").holdId();
        equal(SeatResult.Status.HOLD_INVALID, app.bookSeat("bob", eventId, "A1", holdId).status());
        // Every caller is told it succeeded, but the seat is only ever sold once.
        List<SeatResult> booked = race(i -> app.bookSeat("alice", eventId, "A1", holdId));
        equal((long) USERS, booked.stream().filter(SeatResult::succeeded).count());
        equal(1L, booked.stream().map(SeatResult::bookingId).distinct().count());
        equal(SeatStatus.BOOKED, seatStatus());

        // A stranger is refused outright, not handed the winner's bookingId.
        SeatResult stranger = app.bookSeat("bob", eventId, "A1", holdId);
        equal(SeatResult.Status.SEAT_UNAVAILABLE, stranger.status());
        equal(null, stranger.bookingId());
    }

    private void releasingIsHoldBoundAndIdempotent() {
        String holdId = app.holdSeat("alice", eventId, "A1").holdId();
        equal(SeatResult.Status.HOLD_INVALID,
                app.releaseHold("bob", eventId, "A1", holdId).status());
        equal(SeatResult.Status.HOLD_INVALID,
                app.releaseHold("alice", eventId, "A1", "not-a-hold").status());
        equal(SeatStatus.HELD, seatStatus());

        check(app.releaseHold("alice", eventId, "A1", holdId).succeeded(), "holder may release");
        equal(SeatStatus.AVAILABLE, seatStatus());
        // AVAILABLE cannot say who freed the seat, so a replay reads as success.
        check(app.releaseHold("alice", eventId, "A1", holdId).succeeded(), "release is idempotent");
        check(app.holdSeat("bob", eventId, "A1").succeeded(), "released seat is holdable");
    }

    private void releasingALapsedHoldIsRefused() {
        String holdId = app.holdSeat("alice", eventId, "A1").holdId();
        clock.advance(HOLD);
        equal(SeatResult.Status.HOLD_INVALID,
                app.releaseHold("alice", eventId, "A1", holdId).status());
        // Left for the sweeper, not reclaimed here, but already free to take.
        equal(SeatStatus.AVAILABLE, seatStatus());
        check(app.holdSeat("bob", eventId, "A1").succeeded(), "lapsed seat is holdable");
    }

    private void cancellingFreesTheSeat() {
        String holdId = app.holdSeat("alice", eventId, "A1").holdId();
        String bookingId = app.bookSeat("alice", eventId, "A1", holdId).booking().id();
        equal(SeatResult.Status.NOT_FOUND, app.cancelBooking("bob", bookingId).status());
        check(app.cancelBooking("alice", bookingId).succeeded(), "owner may cancel");
        check(app.cancelBooking("alice", bookingId).succeeded(), "cancel is idempotent");
        equal(SeatStatus.AVAILABLE, seatStatus());
        check(app.holdSeat("bob", eventId, "A1").succeeded(), "freed seat is holdable");
    }

    private void concurrentCancelsFreeTheSeatOnce() throws Exception {
        String holdId = app.holdSeat("alice", eventId, "A1").holdId();
        String bookingId = app.bookSeat("alice", eventId, "A1", holdId).booking().id();
        List<SeatResult> cancels = race(i -> app.cancelBooking("alice", bookingId));
        equal((long) USERS, cancels.stream().filter(SeatResult::succeeded).count());
        equal(SeatStatus.AVAILABLE, seatStatus());
    }

    private void staleCancelCannotFreeSomeoneElsesSeat() {
        String aliceHold = app.holdSeat("alice", eventId, "A1").holdId();
        String aliceBooking = app.bookSeat("alice", eventId, "A1", aliceHold).booking().id();
        check(app.cancelBooking("alice", aliceBooking).succeeded(), "owner may cancel");

        String bobHold = app.holdSeat("bob", eventId, "A1").holdId();
        String bobBooking = app.bookSeat("bob", eventId, "A1", bobHold).booking().id();

        // A booking record no longer says whether it is live, so a replayed
        // cancel reaches the seat. It is only safe because the seat refuses a
        // bookingId it no longer carries.
        check(app.cancelBooking("alice", aliceBooking).succeeded(), "replayed cancel succeeds");
        equal(SeatStatus.BOOKED, seatStatus());
        check(app.cancelBooking("bob", bobBooking).succeeded(), "bob may cancel his own");
        equal(SeatStatus.AVAILABLE, seatStatus());
    }

    private void cleanerReclaimsExpiredHolds() {
        app.holdSeat("alice", eventId, "A1");
        clock.advance(HOLD);
        Event event = app.getEventById(eventId).orElseThrow();
        equal(SeatStatus.AVAILABLE, seatStatus());
        equal(Set.of("A1"), app.getAvailableSeats(eventId));
        equal(1, event.cleanExpiredHolds());
        equal(0, event.cleanExpiredHolds());
        check(app.holdSeat("bob", eventId, "A1").succeeded(), "reclaimed seat is holdable");
    }

    private SeatStatus seatStatus() {
        return app.getEventById(eventId).orElseThrow().getSeatMap().get("A1");
    }

    @Override
    public void close() {
        app.close();
    }

    // Releases USERS copies of the action at once, to maximise contention.
    private <T> List<T> race(IntFunction<T> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        CyclicBarrier gate = new CyclicBarrier(USERS);
        List<Callable<T>> tasks = IntStream.range(0, USERS)
                .<Callable<T>>mapToObj(user -> () -> { gate.await(); return action.apply(user); })
                .toList();
        try {
            List<T> results = new ArrayList<>();
            for (Future<T> done : pool.invokeAll(tasks)) results.add(done.get());
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void equal(Object expected, Object actual) {
        check(Objects.equals(expected, actual), "expected " + expected + " but got " + actual);
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now = Instant.EPOCH;
        void advance(Duration amount) { now = now.plus(amount); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
