package model;

import java.util.Objects;

public record Seat(String id) {
    public Seat {
        Objects.requireNonNull(id, "id must not be null");

        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
    }
}
