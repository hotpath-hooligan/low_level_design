package service;

import entities.CallRequest;
import entities.Direction;
import entities.ElevatorCar;
import entities.ElevatorStatus;
import listener.ElevatorListener;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

public class ElevatorSystem implements AutoCloseable {
    private final List<ElevatorController> controllers;
    private final DispatchStrategy dispatchStrategy;
    private final List<ElevatorListener> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService notifier = Executors.newSingleThreadExecutor(); // listeners run here, never on a car's loop

    public ElevatorSystem(List<ElevatorCar> cars, DispatchStrategy dispatchStrategy, ParkingStrategy parkingStrategy) {
        this.dispatchStrategy = dispatchStrategy;
        this.controllers = IntStream.range(0, cars.size())
                .mapToObj(id -> new ElevatorController(id, cars.get(id), parkingStrategy.parkingFloor(id, cars.size()), this::publish))
                .toList();
    }

    public void start(Clock clock) {
        clock.start(this::tickAll);
    }

    // tick every car in parallel, return once all have finished
    private void tickAll() {
        CompletableFuture.allOf(controllers.stream().map(ElevatorController::tick).toArray(CompletableFuture[]::new)).join();
    }

    public void addListener(ElevatorListener listener) {
        listeners.add(listener);
    }

    public CompletableFuture<Integer> requestElevator(int floor, Direction dir) {
        return dispatchStrategy.selectElevator(controllers, new CallRequest(floor, dir))
                .thenCompose(id -> controllers.get(id).addStop(floor, dir).thenApply(done -> id));
    }

    public CompletableFuture<Void> selectFloor(int elevatorId, int destFloor) {
        return controllers.get(elevatorId).addStop(destFloor);
    }

    private void publish(ElevatorStatus status) {
        notifier.execute(() -> listeners.forEach(l -> l.onUpdate(status)));
    }

    @Override
    public void close() {
        controllers.forEach(ElevatorController::close); // stop the cars first (close the clock before this)
        notifier.close();                               // then deliver what's left
    }
}
