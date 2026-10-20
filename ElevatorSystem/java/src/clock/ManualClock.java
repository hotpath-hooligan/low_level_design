package clock;

import service.Clock;

// ticks only when told to: deterministic, no sleeping (for tests)
public class ManualClock implements Clock {
    private Runnable tick = () -> {};

    @Override
    public void start(Runnable tick) {
        this.tick = tick;
    }

    public void advance(int ticks) {
        for (int i = 0; i < ticks; i++) tick.run();
    }
}
