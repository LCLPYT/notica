package work.lclpnet.kibu.nbs.api;

import work.lclpnet.kibu.hook.Hook;
import work.lclpnet.kibu.hook.HookFactory;
import work.lclpnet.kibu.nbs.data.Layer;
import work.lclpnet.kibu.nbs.data.Note;
import work.lclpnet.kibu.nbs.data.Song;

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

    public SongPlayback(Song song, NotePlayer notePlayer) {
        this.song = song;
        this.notePlayer = notePlayer;

        this.ticks = song.durationTicks();

        double exactTempo = 1000f / song.ticksPerSecond();
        this.period = (int) Math.ceil(exactTempo);
        this.remainder = Math.max(0, period - exactTempo);
    }

    public synchronized void start() {
        if (started) return;
        started = true;

        Thread thread = new Thread(this, "Song Player");
        thread.setDaemon(true);
        thread.start();
    }

    @SuppressWarnings("BusyWait")
    @Override
    public void run() {
        final var layers = song.layers().values();

        while (tick < ticks) {
            final long before = System.currentTimeMillis();
            final int t = tick++;

            for (Layer layer : layers) {
                Note note = layer.notes().get(t);

                if (note == null) continue;

                notePlayer.playNote(song, layer, note);
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
}
