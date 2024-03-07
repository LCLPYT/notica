package work.lclpnet.kibu.nbs.impl;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.PlayerHolder;
import work.lclpnet.kibu.nbs.data.CustomInstrument;
import work.lclpnet.kibu.nbs.data.Layer;
import work.lclpnet.kibu.nbs.data.Note;
import work.lclpnet.kibu.nbs.data.Song;
import work.lclpnet.kibu.nbs.util.NoteHelper;

public class ServerBasicNotePlayer implements NotePlayer, PlayerHolder {

    private ServerPlayerEntity player;
    private final InstrumentSoundProvider soundProvider;
    private final float volume;

    public ServerBasicNotePlayer(ServerPlayerEntity player, InstrumentSoundProvider soundProvider, float volume) {
        this.player = player;
        this.soundProvider = soundProvider;
        this.volume = Math.max(0f, Math.min(1f, volume));
    }

    @Override
    public void setPlayer(ServerPlayerEntity player) {
        synchronized (this) {
            this.player = player;
        }
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        // TODO execute playback on server for ServerController so that this can removed
        server.submit(() -> {
            if (!song.stereo()) {
                playMono(song, layer, note);
                return;
            }

            // TODO
            playMono(song, layer, note);
        });
    }

    private void playMono(Song song, Layer layer, Note note) {
        final byte instrument = note.instrument();

        CustomInstrument custom = song.instruments().custom(instrument);

        SoundEvent sound;

        if (custom != null) {
            sound = soundProvider.getCustomInstrumentSound(custom);
        } else {
            sound = soundProvider.getVanillaInstrumentSound(instrument);
        }

        if (sound == null) return;

        float pitch;

        // support for non-vanilla pitch values not implemented currently
        if (custom != null) {
            pitch = NoteHelper.transposedPitch((byte) (note.key() + custom.key() - 45), note.pitch());
        } else {
            pitch = NoteHelper.transposedPitch(note.key(), note.pitch());
        }

        float volume = layer.volume() * note.velocity() * this.volume / 10000f;

        player.playSound(sound, SoundCategory.RECORDS, volume, pitch);
    }
}
