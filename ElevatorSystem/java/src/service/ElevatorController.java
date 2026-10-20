package service;

import entities.CallRequest;
import entities.CarState;
import entities.Direction;
import entities.ElevatorCar;
import entities.ElevatorStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

// one per car: its event loop is the only thread that touches the car, so the car needs no locks
public class ElevatorController implements AutoCloseable {
    private final int id;
    private final ElevatorCar car;
    private final int homeFloor;
    private final Consumer<ElevatorStatus> onChange;
    private final ExecutorService loop = Executors.newSingleThreadExecutor();

    public ElevatorController(int id, ElevatorCar car, int homeFloor, Consumer<ElevatorStatus> onChange) {
        this.id = id;
        this.car = car;
        this.homeFloor = homeFloor;
        this.onChange = onChange;
    }

    public CompletableFuture<Integer> timeToReach(CallRequest request) {
        return CompletableFuture.supplyAsync(() -> car.timeToReach(request), loop);
    }

    // hall call
    public CompletableFuture<Void> addStop(int floor, Direction dir) {
        return CompletableFuture.runAsync(() -> update(() -> car.addStop(floor, dir)), loop);
    }

    // cab button
    public CompletableFuture<Void> addStop(int floor) {
        return CompletableFuture.runAsync(() -> update(() -> car.addStop(floor)), loop);
    }

    // one tick of time (1 tick = 1 floor), run on the loop
    public CompletableFuture<Void> tick() {
        return CompletableFuture.runAsync(() -> update(() -> {
            car.step();
            if (car.getState() == CarState.IDLE && car.getCurrentFloor() != homeFloor) car.addStop(homeFloor);
        }), loop);
    }

    // apply a change on the loop, report the new status if it changed
    private void update(Runnable change) {
        ElevatorStatus before = status();
        change.run();
        ElevatorStatus after = status();
        if (!after.equals(before)) onChange.accept(after);
    }

    private ElevatorStatus status() {
        return new ElevatorStatus(id, car.getCurrentFloor(), car.getState(), car.getDirection(), car.getNextStop());
    }

    @Override
    public void close() {
        loop.close();
    }
}
