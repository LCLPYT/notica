package work.lclpnet.notica.api;

public interface SongPlayback {

    void start(int startTick);

    void stop();

    void seekTo(int tick, boolean absolute);

    /**
     * @return Whether the playback was stopped by calling {@link #stop()}.
     */
    boolean wasStoppedManually();

    void whenDone(Runnable action);
}
