package work.lclpnet.kibu.nbs.impl;

import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;

public class ClientNotePlayer implements NotePlayer {

    private final InstrumentSoundProvider soundProvider;
    private final float volume;
    private final PlayerConfig playerConfig;

    public ClientNotePlayer(InstrumentSoundProvider soundProvider, float volume, PlayerConfig playerConfig) {
        this.soundProvider = soundProvider;
        this.volume = volume;
        this.playerConfig = playerConfig;
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {

    }
}
