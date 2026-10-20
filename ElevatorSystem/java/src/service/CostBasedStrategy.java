package service;

import entities.CallRequest;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

// pick the car that reaches the caller soonest; each car's movement policy supplies the estimate
public class CostBasedStrategy implements DispatchStrategy {
    @Override
    public CompletableFuture<Integer> selectElevator(List<ElevatorController> controllers, CallRequest request) {
        List<CompletableFuture<Integer>> times = controllers.stream().map(c -> c.timeToReach(request)).toList();
        return CompletableFuture.allOf(times.toArray(CompletableFuture[]::new))
                .thenApply(done -> IntStream.range(0, times.size())
                        .boxed()
                        .min(Comparator.comparingInt(i -> times.get(i).join()))
                        .orElseThrow());
    }
}
