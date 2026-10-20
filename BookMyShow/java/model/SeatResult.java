package model;

public record SeatResult(
        Status status,
        String holdId,
        String bookingId,
        Booking booking
) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        EVENT_STARTED,
        SEAT_UNAVAILABLE,
        HOLD_INVALID
    }

    public static SeatResult of(Status status) {
        return new SeatResult(status, null, null, null);
    }

    public static SeatResult held(String holdId) {
        return new SeatResult(Status.SUCCESS, holdId, null, null);
    }

    public static SeatResult booked(String bookingId) {
        return new SeatResult(Status.SUCCESS, null, bookingId, null);
    }

    public static SeatResult success(Booking booking) {
        return new SeatResult(Status.SUCCESS, null, booking.id(), booking);
    }

    public boolean succeeded() {
        return status == Status.SUCCESS;
    }
}
