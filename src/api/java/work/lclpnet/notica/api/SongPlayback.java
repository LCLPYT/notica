package work.lclpnet.notica.api;

public interface SongPlayback {

    void start(int startTick);

    void stop();

    void seekTo(int tick, boolean absolute);

    boolean isStopped();

    void whenDone(Runnable action);
}
