package work.lclpnet.notica.api.data;

import work.lclpnet.notica.api.Index;

import java.util.OptionalInt;

public interface Song {

    /**
     * @return The total duration of the song, in song ticks.
     */
    int durationTicks();

    /**
     * @return The {@link SongTempo}, describing the tempo of the song sections.
     */
    SongTempo tempo();

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
        return tempo().durationSeconds(0, durationTicks());
    }

    default int paddedDurationTicks() {
        int ticks = durationTicks();
        int interval = Math.max(2, Math.min(signature(), 8)) * 4;
        return ticks + interval - (ticks % interval);
    }

    default float paddedDurationSeconds() {
        return tempo().durationSeconds(0, paddedDurationTicks());
    }

    default OptionalInt lastNoteTick() {
        return layers().stream()
                .flatMapToInt(l -> l.notes().maxIndex().stream())
                .max();
    }
}
