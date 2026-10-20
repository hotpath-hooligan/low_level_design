package entities;

import java.util.NavigableSet;
import java.util.TreeSet;

// keep going while stops lie ahead, reverse when none
public class LookStrategy implements MovementStrategy {
    private static final int STOP_COST = ElevatorCar.DOOR_OPEN_TICKS; // ticks lost per stop (1 tick = 1 floor)

    @Override
    public Direction nextDirection(ElevatorCar car) {
        int floor = car.getCurrentFloor();
        Direction current = car.getDirection();
        TreeSet<Integer> all = new TreeSet<>(car.getUpStops());
        all.addAll(car.getDownStops());
        if (all.isEmpty()) return current;
        if (current == Direction.UP && all.higher(floor) == null) return Direction.DOWN;
        if (current == Direction.DOWN && all.lower(floor) == null) return Direction.UP;
        return current;
    }

    // cost = floors travelled + STOP_COST * stops made before reaching the caller
    @Override
    public int timeToReach(ElevatorCar car, CallRequest req) {
        int pos = car.getCurrentFloor(), f = req.floor(), cost = 0;
        Direction dir = car.getState() == CarState.IDLE
                ? f >= pos ? Direction.UP : Direction.DOWN // idle car heads toward the caller
                : car.getDirection();
        if (car.getState() == CarState.MOVING) { // already passed this floor: start from the next one
            pos += dir == Direction.UP ? 1 : -1;
            cost = 1;
        }

        NavigableSet<Integer> same = dir == Direction.UP ? car.getUpStops() : car.getDownStops(); // served on this run
        NavigableSet<Integer> opp = dir == Direction.UP ? car.getDownStops() : car.getUpStops();  // served after turning

        TreeSet<Integer> all = new TreeSet<>(same);
        all.addAll(opp);
        all.add(f);
        all.add(pos);
        int far = dir == Direction.UP ? all.last() : all.first();  // where this run turns around
        int near = dir == Direction.UP ? all.first() : all.last(); // where the next run turns around

        boolean ahead = dir == Direction.UP ? f >= pos : f <= pos;

        // 1. caller is ahead and going our way: picked up on this run
        if (ahead && req.direction() == dir) {
            return cost + Math.abs(f - pos)
                    + STOP_COST * count(same, pos, f, false);
        }

        // 2. caller wants the other way: run to the far end, turn, come back to them
        if (req.direction() != dir) {
            return cost + Math.abs(far - pos) + Math.abs(far - f)
                    + STOP_COST * (count(same, pos, far, far != f) + count(opp, far, f, false));
        }

        // 3. caller is behind and going our way: far end, back to the near end, turn again up to them
        return cost + Math.abs(far - pos) + Math.abs(far - near) + Math.abs(f - near)
                + STOP_COST * (count(same, pos, far, true) + count(opp, far, near, near != f) + count(same, near, f, false));
    }

    private static int count(NavigableSet<Integer> stops, int from, int to, boolean toInc) {
        return from <= to ? stops.subSet(from, true, to, toInc).size() : stops.subSet(to, toInc, from, true).size();
    }
}
