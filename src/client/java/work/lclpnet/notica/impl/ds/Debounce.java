package work.lclpnet.notica.impl.ds;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Debounce {

    private final int delayMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform()
            .daemon()
            .name("Notica Debounce")
            .factory());
    private ScheduledFuture<?> future;

    public Debounce(int delayMs) {
        this.delayMs = delayMs;
    }

    public synchronized void debounce(Runnable action) {
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }

        future = scheduler.schedule(action, delayMs, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        scheduler.shutdown();
    }
}
