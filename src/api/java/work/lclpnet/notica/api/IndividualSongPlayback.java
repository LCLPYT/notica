package work.lclpnet.notica.api;

import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.notica.api.data.*;

import java.util.Objects;

import static java.lang.Math.*;
import static java.lang.System.nanoTime;
import static java.lang.Thread.sleep;

public class IndividualSongPlayback implements Runnable, SongPlayback {

    private final Song song;
    private final NotePlayer notePlayer;
    private final int durationTicks;
    private final LoopConfig loopConfig;
    private boolean started = false;
    private int tick = 0;
    private double tempoNs;
    private double expectedNextNs = 0;
    private volatile Hook<Runnable> onComplete = null;
    private volatile Thread thread = null;
    private volatile boolean stopped = false;

    public IndividualSongPlayback(Song song, NotePlayer notePlayer) {
        this(song, notePlayer, LoopOverride.DEFAULT);
    }

    public IndividualSongPlayback(Song song, NotePlayer notePlayer, LoopOverride loopOverride) {
        this.song = Objects.requireNonNull(song, "Song must not be null");
        this.notePlayer = Objects.requireNonNull(notePlayer, "NotePlayer must not be null");

        this.durationTicks = song.durationTicks();
        this.loopConfig = loopOverride.override(song.loopConfig());

        updateTempo(song.tempo().tempoAt(0));
    }

    private void updateTempo(float ticksPerSecond) {
        this.tempoNs = 1.e+9 / ticksPerSecond;
    }

    @Override
    public synchronized void start(int startTick) {
        if (started) return;
        started = true;

        tick = startTick;

        thread = new Thread(this, "Song Player");
        thread.start();
    }

    @Override
    public synchronized void stop() {
        if (!started) return;
        started = false;
        stopped = true;

        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            thread = null;
        }
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        int loopAmount = loopConfig.loopCount();
        final boolean shouldLoop = loopConfig.enabled();
        final int endTick;

        if (shouldLoop) {
            int interval = max(2, min(8, song.signature())) * 4;
            endTick = durationTicks + interval - (durationTicks % interval);
        } else {
            endTick = durationTicks + 1;
        }

        while (started && tick < endTick) {
            final int t = tick++;

            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(t);

                if (note == null) continue;

                notePlayer.playNote(song, layer, note);
            }

            if (notePlayer instanceof AggregatingPlayer aggregatingPlayer) {
                aggregatingPlayer.finishAggregation();
            }

            if (shouldLoop && tick == endTick) {
                boolean infinite = loopConfig.infinite();

                if (infinite || loopAmount > 0) {
                    if (!infinite) loopAmount--;

                    tick = loopConfig.loopStartTick();
                }
            }

            if (song.tempo().changeAt(t)) {
                updateTempo(song.tempo().tempoAt(t));
            }

            long after = nanoTime();
            long delayNs;

            if (expectedNextNs > 0) {
                delayNs = (long) (after - expectedNextNs);
            } else {
                delayNs = 0;
                expectedNextNs = after;
            }

            expectedNextNs += tempoNs;

            double waitExact = tempoNs - delayNs;
            long waitMs = round(waitExact * 1e-6);
            int waitNs = (int) round(waitExact - waitMs * 1e+6);

            waitNs = max(0, min(999999, waitNs));

            if (waitMs > 0) {
                try {
                    sleep(waitMs, waitNs);
                } catch (InterruptedException ignored) {}
            }
        }

        if (onComplete != null) {
            onComplete.invoker().run();
        }
    }

    @Override
    public void whenDone(Runnable action) {
        getOrCreateHook().register(action);
    }

    private Hook<Runnable> getOrCreateHook() {
        if (onComplete != null) return onComplete;

        synchronized (this) {
            if (onComplete != null) return onComplete;

            onComplete = runnableHook();
        }

        return onComplete;
    }

    public static Hook<Runnable> runnableHook() {
        return HookFactory.createArrayBacked(Runnable.class, hooks -> () -> {
            for (var hook : hooks) {
                hook.run();
            }
        });
    }

    @Override
    public synchronized boolean wasStoppedManually() {
        return stopped;
    }

    @Override
    public synchronized void seekTo(int ticks, boolean absolute) {
        ticks = max(0, absolute ? ticks : this.tick + ticks);

        this.tick = ticks;
        this.expectedNextNs = 0;

        updateTempo(song.tempo().tempoAt(ticks));
    }
}
