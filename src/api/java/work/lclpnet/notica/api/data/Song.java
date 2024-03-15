package work.lclpnet.notica.api.data;

import work.lclpnet.notica.api.Index;

public interface Song {

    /**
     * @return The total duration of the song, in song ticks.
     */
    int durationTicks();

    /**
     * @return The amount of ticks per second. If this is 10, there will be a note every 0.1 seconds or 2 vanilla game ticks.
     */
    float ticksPerSecond();

    /**
     * @return Metadata of this song, includes author, title etc.
     */
    SongMeta metaData();

    /**
     * @return Configuration of song playback looping.
     */
    LoopConfig loopConfig();

    /**
     * @return The individual note layers of this song.
     */
    Index<? extends Layer> layers();

    /**
     * @return The instruments configuration of this song. Includes custom instruments.
     */
    Instruments instruments();

    /**
     * @return Whether this song has notes that should be played with panning.
     */
    boolean stereo();

    /**
     * @return The time signature of the song in quarters. When this is 3, time signature will be 3/4.
     */
    byte signature();

    /**
     * @return The amount of seconds this song is long
     */
    default float durationSeconds() {
        return durationTicks() / ticksPerSecond();
    }

    default int paddedDurationTicks() {
        int ticks = durationTicks();
        int interval = Math.max(2, Math.min(signature(), 8)) * 4;
        return ticks + interval - (ticks % interval);
    }

    default float paddedDurationSeconds() {
        return paddedDurationTicks() / ticksPerSecond();
    }
}
