package services;

import model.Event;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExpiredHoldCleaner implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(
            ExpiredHoldCleaner.class.getName()
    );
    private static final Duration CLEANUP_INTERVAL = Duration.ofSeconds(30);

    private final EventService eventService;
    private final ScheduledExecutorService executor;

    private ExpiredHoldCleaner(EventService eventService) {
        this.eventService = eventService;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "expired-hold-cleaner");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Builds a cleaner and starts it sweeping. Scheduling from a factory rather
     * than the constructor keeps {@code this} from reaching the cleaner thread
     * before construction finishes.
     */
    public static ExpiredHoldCleaner start(EventService eventService) {
        ExpiredHoldCleaner cleaner = new ExpiredHoldCleaner(eventService);
        cleaner.executor.scheduleWithFixedDelay(
                cleaner::clean,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
        return cleaner;
    }

    private void clean() {
        try {
            for (Event event : eventService.getEvents()) {
                event.cleanExpiredHolds();
            }
        } catch (RuntimeException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Expired hold cleanup failed; will retry on next cycle",
                    exception
            );
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
