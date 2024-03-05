package work.lclpnet.kibu.nbs.controller;

import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

public interface Controller {

    /**
     * Play a song with a given volume.
     * @param song A {@link SongDescriptor} describing the song that should be played.
     * @param volume The volume of the song. Ranges [0..1]
     */
    void playSong(SongDescriptor song, float volume);

    /**
     * Play a song at a given position.
     * The position may also change over time.
     * @param song A {@link SongDescriptor} describing the song that should be played.
     * @param position Where to play the song. This may be dynamic.
     * @param volume The volume of the song. Ranges [0..1]
     */
    void playSongAt(SongDescriptor song, Position position, float volume);
}
