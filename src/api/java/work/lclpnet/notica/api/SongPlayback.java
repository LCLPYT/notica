package work.lclpnet.notica.api;

import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.LoopConfig;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

import java.util.Objects;

public class SongPlayback implements Runnable {

    private final Song song;
    private final NotePlayer notePlayer;
    private final int ticks;
    private final int period;
    private final double remainder;
    private boolean started = false;
    private int tick = 0;
    private double extraMs = 0f;
    private volatile Hook<Runnable> onComplete = null;
    private volatile Thread thread = null;
    private volatile boolean stopped = false;

    public SongPlayback(Song song, NotePlayer notePlayer) {
        this.song = Objects.requireNonNull(song, "Song must not be null");
        this.notePlayer = Objects.requireNonNull(notePlayer, "NotePlayer must not be null");

        this.ticks = song.durationTicks();

        double exactTempo = 1000f / song.ticksPerSecond();
        this.period = (int) Math.ceil(exactTempo);
        this.remainder = Math.max(0, period - exactTempo);
    }

    public void start(int startTick) {
        synchronized (this) {
            if (started) return;
            started = true;

            tick = startTick;

            thread = new Thread(this, "Song Player");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void stop() {
        synchronized (this) {
            if (!started) return;
            started = false;
            stopped = true;

            if (thread != null && thread.isAlive()) {
                thread.interrupt();
                thread = null;
            }
        }
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        LoopConfig loopConfig = song.loopConfig();

        int loopAmount = loopConfig.loopCount();
        final boolean shouldLoop = loopConfig.enabled();
        final int endTick;

        if (shouldLoop) {
            int interval = Math.max(2, Math.min(song.signature(), 8)) * 4;
            endTick = ticks + interval - (ticks % interval);
        } else {
            endTick = ticks + 1;
        }

        while (started && tick < endTick) {
            final long before = System.currentTimeMillis();
            final int t = tick++;

            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(t);

                if (note == null) continue;

                notePlayer.playNote(song, layer, note);
            }

            if (shouldLoop && tick == endTick) {
                boolean infinite = loopConfig.infinite();

                if (infinite || loopAmount > 0) {
                    if (!infinite) loopAmount--;

                    tick = loopConfig.loopStartTick();
                }
            }

            long elapsed = System.currentTimeMillis() - before;

            if (extraMs >= 1.0) {
                int w = (int) Math.floor(extraMs);
                elapsed += w;
                extraMs -= w;
            }

            long waitMs = period - elapsed;
            extraMs += remainder;

            if (waitMs <= 0) continue;

            try {
                Thread.sleep(period);
            } catch (InterruptedException ignored) {}
        }

        if (onComplete != null) {
            onComplete.invoker().run();
        }
    }

    public void whenDone(Runnable action) {
        getOrCreateHook().register(action);
    }

    private Hook<Runnable> getOrCreateHook() {
        if (onComplete != null) return onComplete;

        synchronized (this) {
            if (onComplete != null) return onComplete;

            onComplete = HookFactory.createArrayBacked(Runnable.class, callbacks -> () -> {
                for (var callback : callbacks) {
                    callback.run();
                }
            });
        }

        return onComplete;
    }

    public boolean isStopped() {
        synchronized (this) {
            return stopped;
        }
    }
}
