import model.Event;
import model.Seat;
import model.SeatResult;
import services.BookingService;
import services.EventService;
import services.ExpiredHoldCleaner;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class BookMyShow implements AutoCloseable {
    private final EventService eventService;
    private final BookingService bookingService;
    private final ExpiredHoldCleaner expiredHoldCleaner;

    public BookMyShow(Clock clock) {
        eventService = new EventService(clock);
        bookingService = new BookingService(eventService);
        expiredHoldCleaner = ExpiredHoldCleaner.start(eventService);
    }

    public Event addEvent(
            String name,
            Instant startsAt,
            Instant endsAt,
            Duration holdDuration,
            Collection<String> seatIds
    ) {
        Objects.requireNonNull(seatIds, "seatIds must not be null");
        List<Seat> seats = seatIds.stream().map(Seat::new).toList();
        return eventService.addEvent(
                name,
                startsAt,
                endsAt,
                holdDuration,
                seats
        );
    }

    public List<Event> getEvents() {
        return eventService.getEvents();
    }

    public Optional<Event> getEventById(String eventId) {
        return eventService.getEventById(eventId);
    }

    public Set<String> getAvailableSeats(String eventId) {
        return eventService.getEventById(eventId)
                .map(Event::getAvailableSeats)
                .orElseGet(Set::of);
    }

    public SeatResult holdSeat(String userId, String eventId, String seatId) {
        return bookingService.holdSeat(userId, eventId, seatId);
    }

    public SeatResult bookSeat(
            String userId,
            String eventId,
            String seatId,
            String holdId
    ) {
        return bookingService.bookSeat(userId, eventId, seatId, holdId);
    }

    public SeatResult releaseHold(
            String userId,
            String eventId,
            String seatId,
            String holdId
    ) {
        return bookingService.releaseHold(userId, eventId, seatId, holdId);
    }

    public SeatResult cancelBooking(String userId, String bookingId) {
        return bookingService.cancelBooking(userId, bookingId);
    }

    @Override
    public void close() {
        expiredHoldCleaner.close();
    }
}
