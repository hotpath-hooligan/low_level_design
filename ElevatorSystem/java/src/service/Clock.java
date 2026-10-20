package service;

// decides when the system ticks; real time in production, manual in tests
@FunctionalInterface
public interface Clock {
    void start(Runnable tick);
}
