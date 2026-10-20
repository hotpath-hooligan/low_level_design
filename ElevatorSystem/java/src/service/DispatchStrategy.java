package service;

import entities.CallRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface DispatchStrategy {
    // completes with the index of the controller that should answer the call
    CompletableFuture<Integer> selectElevator(List<ElevatorController> controllers, CallRequest request);
}
