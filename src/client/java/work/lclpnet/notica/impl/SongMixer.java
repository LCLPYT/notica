package work.lclpnet.notica.impl;

public interface SongMixer {

    int mixTicks(int startTick, int endTick, int frameOffset);

    void setSongVolume(float volume);
}
