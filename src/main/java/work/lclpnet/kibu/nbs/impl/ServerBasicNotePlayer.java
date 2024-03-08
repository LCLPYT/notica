package work.lclpnet.kibu.nbs.impl;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.api.NotePlayer;
import work.lclpnet.kibu.nbs.api.PlayerConfig;
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
    private final PlayerConfig playerConfig;

    public ServerBasicNotePlayer(ServerPlayerEntity player, InstrumentSoundProvider soundProvider, float volume,
                                 PlayerConfig playerConfig) {
        this.player = player;
        this.soundProvider = soundProvider;
        this.volume = Math.max(0f, Math.min(1f, volume));
        this.playerConfig = playerConfig;
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

        if (!song.stereo()) {
            playMono(song, layer, note);
            return;
        }

        // TODO
        playMono(song, layer, note);
    }

    private void playMono(Song song, Layer layer, Note note) {
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

        short pitch = note.pitch();
        float vanillaPitch;

        if (NoteHelper.isOutsideVanillaRange(key, pitch) && playerConfig.isExtendedRangeSupported()) {
            // play extended octave range sound
            sound = soundProvider.getExtendedSound(sound, key, pitch);
            vanillaPitch = NoteHelper.normalizedPitch(key, pitch);
        } else {
            vanillaPitch = NoteHelper.transposedPitch(key, pitch);
        }

        float volume = layer.volume() * note.velocity() * 1e-4f * this.volume * playerConfig.getVolume();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        float panning = ((layer.panning() + note.panning()) * 0.5f - 100) / 100;  // [-1, 1], 0=center

        if (Math.abs(panning) >= 1e-3) {
            double yaw = Math.toRadians(player.getYaw() - 90f);  // rotate 90 degrees ccw

            x += Math.sin(yaw) * panning;
            z += -Math.cos(yaw) * panning;
        }

        playSoundAt(sound, x, y, z, volume, vanillaPitch);
    }

    private void playSoundAt(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        var packet = new PlaySoundS2CPacket(Registries.SOUND_EVENT.getEntry(sound), SoundCategory.RECORDS, x, y, z,
                volume, pitch, player.getRandom().nextLong());

        player.networkHandler.sendPacket(packet);
    }
}
