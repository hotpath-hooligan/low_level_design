package listener;

import entities.ElevatorStatus;

@FunctionalInterface
public interface ElevatorListener {
    void onUpdate(ElevatorStatus status);
}
