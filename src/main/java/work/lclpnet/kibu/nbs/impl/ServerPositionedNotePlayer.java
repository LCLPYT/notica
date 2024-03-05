package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Position;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.data.Layer;
import work.lclpnet.kibu.nbs.data.Note;
import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.api.PlayerHolder;

public class ServerPositionedNotePlayer implements NotePlayer, PlayerHolder {

    private ServerPlayerEntity player;
    private final Position position;

    public ServerPositionedNotePlayer(ServerPlayerEntity player, Position position) {
        this.player = player;
        this.position = position;
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        synchronized (this) {
            this.player = player;
        }
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {

    }
}
