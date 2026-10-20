package service;

// spread idle cars evenly from ground to top floor
public class ZonedParkingStrategy implements ParkingStrategy {
    private final int numFloors;

    public ZonedParkingStrategy(int numFloors) {
        this.numFloors = numFloors;
    }

    @Override
    public int parkingFloor(int elevatorId, int totalCars) {
        if (totalCars == 1) return 0;
        return elevatorId * (numFloors - 1) / (totalCars - 1);
    }
}
