package work.lclpnet.kibu.nbs.impl;

import net.minecraft.util.math.Position;
import org.slf4j.Logger;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.api.SongPlayback;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.controller.Controller;
import work.lclpnet.kibu.nbs.util.PlayerConfigEntry;

import java.util.Set;

public class ClientController implements Controller {

    private final ClientSongResolver songResolver;
    private final Logger logger;
    private final InstrumentSoundProvider soundProvider;
    private final PlayerConfig playerConfig = new PlayerConfigEntry();

    public ClientController(ClientSongResolver songResolver, Logger logger, InstrumentSoundProvider soundProvider) {
        this.songResolver = songResolver;
        this.logger = logger;
        this.soundProvider = soundProvider;
    }

    @Override
    public void playSong(SongDescriptor descriptor, float volume) {
        Song song = songResolver.resolve(descriptor);

        if (song == null) {
            logger.error("Unknown song {}", descriptor);
            return;
        }

        stopSong(descriptor);

        NotePlayer notePlayer = new ClientNotePlayer(soundProvider, volume, playerConfig);
        SongPlayback playback = new SongPlayback(song, notePlayer);

        playback.start();
    }

    @Override
    public void playSongAt(SongDescriptor song, Position position, float volume) {

    }

    @Override
    public void stopSong(SongDescriptor song) {

    }

    @Override
    public Set<SongDescriptor> getPlayingSongs() {
        return null;
    }
}
