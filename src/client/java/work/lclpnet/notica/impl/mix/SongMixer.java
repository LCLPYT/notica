package work.lclpnet.notica.impl.mix;

public interface SongMixer {

    int mixTicks(int startTick, int endTick, int frameOffset);

    void setSongVolume(float volume);
}
