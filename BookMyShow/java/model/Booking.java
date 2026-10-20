package model;

import java.util.Objects;

/**
 * The immutable facts of a booking: who bought what. Whether the booking is
 * still live is deliberately not stored here — the seat's own state carries the
 * bookingId, so it is the single record of that, and cancelling is one write.
 */
public record Booking(
        String id,
        String userId,
        String eventId,
        Seat seat
) {
    public Booking {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(seat, "seat must not be null");

        if (id.isBlank() || userId.isBlank() || eventId.isBlank()) {
            throw new IllegalArgumentException("ids must not be blank");
        }
    }

    public boolean isOwnedBy(String requestedUserId) {
        return userId.equals(requestedUserId);
    }
}
