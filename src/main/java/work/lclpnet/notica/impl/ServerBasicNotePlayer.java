package work.lclpnet.notica.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.NoteHelper;

import java.util.Set;

import static java.lang.Math.abs;

public class ServerBasicNotePlayer implements NotePlayer {

    private final InstrumentSoundProvider soundProvider;
    private final float volume;
    private final Set<SongPlayerRef> players;

    public ServerBasicNotePlayer(Set<SongPlayerRef> players, InstrumentSoundProvider soundProvider, float volume) {
        this.soundProvider = soundProvider;
        this.volume = Math.max(0f, Math.min(1f, volume));
        this.players = players;
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {
        if (players.isEmpty()) return;

        final byte instrument = note.instrument();
        CustomInstrument custom = song.instruments().custom(instrument);

        SoundEvent sound;
        byte key;

        if (custom != null) {
            sound = soundProvider.getCustomInstrumentSound(custom);
            key = (byte) (note.key() + custom.key() - 45);
        } else {
            sound = soundProvider.getVanillaInstrumentSound(instrument);
            key = note.key();
        }

        if (sound == null) return;

        float volume = layer.volume() * note.velocity() * 1e-4f * this.volume;
        float panning = NoteHelper.normalizePanning(layer.panning(), note.panning());
        short pitch = note.pitch();

        if (volume <= 0) return;

        synchronized (this) {
            for (SongPlayerRef playerRef : players) {
                playSoundFor(playerRef, panning, sound, volume, key, pitch);
            }
        }
    }

    private void playSoundFor(SongPlayerRef playerRef, float panning, SoundEvent sound, float volume, byte key, short pitch) {
        ServerPlayer player = playerRef.getPlayer();
        PlayerConfig config = playerRef.getConfig();

        float vanillaPitch;

        if (NoteHelper.isOutsideVanillaRange(key, pitch) && config.isExtendedRangeSupported()) {
            // play extended octave range sound
            sound = soundProvider.getExtendedSound(sound, key, pitch);
            vanillaPitch = NoteHelper.normalizedPitch(key, pitch);
        } else {
            vanillaPitch = NoteHelper.transposedPitch(key, pitch);
        }

        volume *= config.getVolume();

        if (volume <= 0) return;

        double x = player.getX();
        double y = player.getY();  // eyeY sounds awfully, as sound positions are only sent as integers
        double z = player.getZ();

        if (abs(panning) >= 1e-3) {
            double yaw = Math.toRadians(player.getYRot() - 90f);  // rotate 90 degrees ccw

            x += Math.sin(yaw) * panning * 2;
            z -= Math.cos(yaw) * panning * 2;
        }

        var packet = new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.RECORDS, x, y, z,
                volume, vanillaPitch, player.getRandom().nextLong());

        player.connection.send(packet);
    }

    public void removePlayer(SongPlayerRef player) {
        players.remove(player);
    }
}
