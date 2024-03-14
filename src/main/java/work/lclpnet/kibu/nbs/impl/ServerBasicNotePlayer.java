package work.lclpnet.kibu.nbs.impl;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
import work.lclpnet.kibu.nbs.api.data.CustomInstrument;
import work.lclpnet.kibu.nbs.api.data.Layer;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.util.NoteHelper;

import java.util.Set;

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
        float panning = ((layer.panning() + note.panning()) * 0.5f - 100) / 100;  // [-1, 1], 0=center
        short pitch = note.pitch();

        synchronized (this) {
            for (SongPlayerRef playerRef : players) {
                playSoundFor(playerRef, panning, sound, volume, key, pitch);
            }
        }
    }

    private void playSoundFor(SongPlayerRef playerRef, float panning, SoundEvent sound, float volume, byte key, short pitch) {
        ServerPlayerEntity player = playerRef.getPlayer();
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

        double x = player.getX();
        double y = player.getY();  // eyeY sounds awfully, as sound positions are only sent as integers
        double z = player.getZ();

        if (Math.abs(panning) >= 1e-3) {
            double yaw = Math.toRadians(player.getYaw() - 90f);  // rotate 90 degrees ccw

            x += Math.sin(yaw) * panning;
            z -= Math.cos(yaw) * panning;
        }

        var packet = new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(sound), SoundCategory.RECORDS, x, y, z,
                volume, vanillaPitch, player.getRandom().nextLong());

        player.networkHandler.sendPacket(packet);
    }

    public void removePlayer(SongPlayerRef player) {
        players.remove(player);
    }
}
