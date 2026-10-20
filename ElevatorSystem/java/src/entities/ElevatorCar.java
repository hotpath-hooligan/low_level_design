package entities;

import java.util.Collections;
import java.util.NavigableSet;
import java.util.TreeSet;

// not thread-safe: only its ElevatorController's loop touches it
public class ElevatorCar {
    public static final int DOOR_OPEN_TICKS = 5; // how long doors stay open at a stop

    private int currentFloor;
    private CarState state = CarState.IDLE;
    private Direction direction = Direction.UP;
    private int doorTicks; // ticks the doors have been open
    private final TreeSet<Integer> upStops = new TreeSet<>();   // served while moving UP
    private final TreeSet<Integer> downStops = new TreeSet<>(); // served while moving DOWN
    private final MovementStrategy movementStrategy;

    public ElevatorCar(MovementStrategy movementStrategy, int startFloor) {
        this.movementStrategy = movementStrategy;
        this.currentFloor = startFloor;
    }

    public int getCurrentFloor() {
        return currentFloor;
    }

    public CarState getState() {
        return state;
    }

    public Direction getDirection() {
        return direction;
    }

    public NavigableSet<Integer> getUpStops() {
        return Collections.unmodifiableNavigableSet(upStops);
    }

    public NavigableSet<Integer> getDownStops() {
        return Collections.unmodifiableNavigableSet(downStops);
    }

    public int timeToReach(CallRequest request) {
        return movementStrategy.timeToReach(this, request);
    }

    private int stopCount() {
        return upStops.size() + downStops.size();
    }

    public Integer getNextStop() {
        if (state == CarState.IDLE) return null;
        if (direction == Direction.UP) {
            Integer next = upStops.ceiling(currentFloor);
            return next != null || downStops.isEmpty() ? next : downStops.last();
        }
        Integer next = downStops.floor(currentFloor);
        return next != null || upStops.isEmpty() ? next : upStops.first();
    }

    // hall call: rider already chose a direction
    public void addStop(int floor, Direction dir) {
        (dir == Direction.UP ? upStops : downStops).add(floor);
        if (state == CarState.IDLE) {
            state = CarState.MOVING;
            arrive(); // the stop may be on this floor
        }
    }

    // cab button / parking: direction is relative to where the car is
    public void addStop(int floor) {
        addStop(floor, floor > currentFloor ? Direction.UP : Direction.DOWN);
    }

    public void step() {
        switch (state) {
            case IDLE -> {}
            case MOVING -> {
                currentFloor += direction == Direction.UP ? 1 : -1;
                arrive();
            }
            case DOORS_OPEN -> { // doors close, unless someone here still needs to board
                if (++doorTicks < DOOR_OPEN_TICKS) return;
                state = stopCount() == 0 ? CarState.IDLE : CarState.MOVING;
                if (state == CarState.MOVING) arrive();
            }
        }
    }

    // on reaching a floor: serve stops for our direction, turn around if nothing lies ahead
    private void arrive() {
        boolean stop = (direction == Direction.UP ? upStops : downStops).remove(currentFloor);
        direction = movementStrategy.nextDirection(this);
        stop |= (direction == Direction.UP ? upStops : downStops).remove(currentFloor); // turnaround pickup
        if (stop) {
            state = CarState.DOORS_OPEN;
            doorTicks = 0;
        }
    }
}
