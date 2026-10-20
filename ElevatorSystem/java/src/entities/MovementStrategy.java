package entities;

// how a car moves; owned by ElevatorCar, its only user
public interface MovementStrategy {
    Direction nextDirection(ElevatorCar car);

    // ticks until the car picks up the caller, following this policy's route
    int timeToReach(ElevatorCar car, CallRequest request);
}
