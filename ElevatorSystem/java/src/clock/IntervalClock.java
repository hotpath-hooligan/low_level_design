package clock;

import service.Clock;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// ticks every period on its own thread
public class IntervalClock implements Clock, AutoCloseable {
    private final Duration period;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public IntervalClock(Duration period) {
        this.period = period;
    }

    @Override
    public void start(Runnable tick) {
        scheduler.scheduleAtFixedRate(tick, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void close() {
        scheduler.close();
    }
}
