package work.lclpnet.kibu.nbs.controller;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.impl.SongDescriptor;

import java.util.Optional;
import java.util.Set;

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

    void stopSong(SongDescriptor song);

    Set<SongDescriptor> getPlayingSongs();

    default Optional<SongDescriptor> getPlayingSongById(Identifier id) {
        return getPlayingSongs().stream()
                .filter(descriptor -> id.equals(descriptor.id()))
                .findAny();
    }
}
