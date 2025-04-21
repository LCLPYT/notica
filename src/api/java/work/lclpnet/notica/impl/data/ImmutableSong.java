package work.lclpnet.notica.impl.data;

import work.lclpnet.notica.api.Index;
import work.lclpnet.notica.api.data.*;

/**
 * An immutable song object representing a note block song.
 * @param durationTicks The total duration of the song, in song ticks.
 * @param tempo The {@link SongTempo}, describing the tempo of the song sections.
 * @param metaData Metadata of this song, includes author, title etc.
 * @param loopConfig Configuration of song playback looping.
 * @param layers The individual note layers of this song.
 * @param instruments The instruments configuration of this song. Includes custom instruments.
 * @param stereo Whether this song has notes that should be played with panning.
 * @param signature The time signature of the song in quarters. When this is 3, time signature will be 3/4.
 */
public record ImmutableSong(int durationTicks, SongTempo tempo, SongMeta metaData, LoopConfig loopConfig,
                            Index<? extends Layer> layers, Instruments instruments, boolean stereo, byte signature) implements Song {

}
