package entities;

public record ElevatorStatus(int elevatorId, int floor, CarState state, Direction direction, Integer nextStop) {
}
