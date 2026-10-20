package services;

import model.Booking;
import model.SeatResult;
import model.Event;
import model.Seat;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BookingService {
    private final EventService eventService;

    private final ConcurrentMap<String, Booking> bookingsById =
            new ConcurrentHashMap<>();

    public BookingService(EventService eventService) {
        this.eventService = Objects.requireNonNull(eventService);
    }

    public SeatResult holdSeat(String userId, String eventId, String seatId) {
        Event event = eventService.getEventById(eventId).orElse(null);
        if (event == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        if (event.hasStarted()) {
            return SeatResult.of(SeatResult.Status.EVENT_STARTED);
        }
        return event.holdSeat(userId, seatId);
    }

    public SeatResult bookSeat(
            String userId,
            String eventId,
            String seatId,
            String holdId
    ) {
        Event event = eventService.getEventById(eventId).orElse(null);
        if (event == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        Seat seat = event.getSeatById(seatId);
        if (seat == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }

        String proposedBookingId = UUID.randomUUID().toString();
        Booking candidate = new Booking(
                proposedBookingId,
                userId,
                eventId,
                seat
        );

        // Publish before claiming. Flipping the seat to BOOKED is the commit
        // point: the moment it happens, a concurrent retry of the same hold can
        // read our bookingId off the seat and look it up, so the record has to
        // be there already.
        bookingsById.put(proposedBookingId, candidate);

        // Withdrawing a candidate no seat points at is hygiene, not correctness
        // -- liveness is read off the seat, so an abandoned record already reads
        // as not live. It runs in a finally so a throw here cannot strand one.
        boolean claimedByUs = false;
        try {
            SeatResult claimed = event.confirmHold(
                    userId,
                    seatId,
                    holdId,
                    proposedBookingId
            );
            claimedByUs = proposedBookingId.equals(claimed.bookingId());
            if (!claimed.succeeded()) {
                return claimed;
            }

            // On a retry the winner published its record before it claimed the
            // seat, and never withdraws it, so this lookup cannot miss.
            return SeatResult.success(claimedByUs
                    ? candidate
                    : bookingsById.get(claimed.bookingId()));
        } finally {
            if (!claimedByUs) {
                bookingsById.remove(proposedBookingId);
            }
        }
    }

    public SeatResult releaseHold(
            String userId,
            String eventId,
            String seatId,
            String holdId
    ) {
        Event event = eventService.getEventById(eventId).orElse(null);
        if (event == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }
        return event.releaseHold(userId, seatId, holdId);
    }

    public SeatResult cancelBooking(String userId, String bookingId) {
        Booking booking = bookingsById.get(bookingId);
        // Not distinguishing "missing" from "not yours" keeps the API from
        // confirming that a booking id exists to someone who does not own it.
        if (booking == null || !booking.isOwnedBy(userId)) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }

        Event event = eventService.getEventById(booking.eventId()).orElse(null);
        if (event == null) {
            return SeatResult.of(SeatResult.Status.NOT_FOUND);
        }

        // Releasing the seat is the whole cancellation: the seat is the only
        // record of whether this booking is still live, so there is nothing
        // else to keep in step with it. Its own transition already elects a
        // single winner, and the record proves the seat exists, so the one
        // failure left is "someone already released it" -- which is what this
        // caller asked for, and is why cancelling twice reports success.
        event.cancelBookedSeat(userId, booking.seat().id(), bookingId);
        return SeatResult.success(booking);
    }
}
