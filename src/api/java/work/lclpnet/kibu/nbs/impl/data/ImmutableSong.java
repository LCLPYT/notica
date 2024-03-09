package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.*;

import java.util.Map;

/**
 * An immutable song object representing a note block song.
 * @param durationTicks The total duration of the song, in song ticks.
 * @param ticksPerSecond The amount of ticks per second. If this is 10, there will be a note every 0.1 seconds or 2 vanilla game ticks.
 * @param metaData Metadata of this song, includes author, title etc.
 * @param loopConfig Configuration of song playback looping.
 * @param layers The individual note layers of this song.
 * @param instruments The instruments configuration of this song. Includes custom instruments.
 * @param stereo Whether this song has notes that should be played with panning.
 * @param signature The time signature of the song in quarters. When this is 3, time signature will be 3/4.
 */
public record ImmutableSong(int durationTicks, float ticksPerSecond, SongMeta metaData, LoopConfig loopConfig,
                            Map<Integer, Layer> layers, Instruments instruments, boolean stereo, byte signature) implements Song {

}
