package services;

import model.Event;
import model.Seat;
import model.SeatInventory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventService {
    private final Map<String, Event> eventsById = new ConcurrentHashMap<>();
    private final Clock clock;

    public EventService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public Event addEvent(
            String name,
            Instant startsAt,
            Instant endsAt,
            Duration holdDuration,
            Collection<Seat> seats
    ) {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(startsAt, "startsAt must not be null");
        Objects.requireNonNull(endsAt, "endsAt must not be null");
        Objects.requireNonNull(holdDuration, "holdDuration must not be null");
        Objects.requireNonNull(seats, "seats must not be null");

        if (!startsAt.isBefore(endsAt)) {
            throw new IllegalArgumentException("startsAt must be before endsAt");
        }
        if (holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("holdDuration must be positive");
        }
        if (seats.isEmpty()) {
            throw new IllegalArgumentException("seats must not be empty");
        }

        Set<String> seatIds = new HashSet<>();
        for (Seat seat : seats) {
            Objects.requireNonNull(seat, "seats must not contain null");
            if (!seatIds.add(seat.id())) {
                throw new IllegalArgumentException(
                        "duplicate seat id: " + seat.id()
                );
            }
        }

        String id = UUID.randomUUID().toString();
        SeatInventory seatInventory = new SeatInventory(
                seats,
                clock,
                holdDuration
        );
        Event event = new Event(id, name, startsAt, endsAt, seatInventory, clock);
        eventsById.put(id, event);
        return event;
    }

    public List<Event> getEvents() {
        return List.copyOf(eventsById.values());
    }

    public Optional<Event> getEventById(String eventId) {
        return eventId == null
                ? Optional.empty()
                : Optional.ofNullable(eventsById.get(eventId));
    }
}
