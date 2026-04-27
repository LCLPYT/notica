package work.lclpnet.notica.impl;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.NoteHelper;

import java.util.Set;

import static java.lang.Math.clamp;

public class ServerBasicNotePlayer implements NotePlayer {

    private final InstrumentSoundProvider soundProvider;
    private final float volume;
    private final Set<SongPlayerRef> players;
    private final SoundPositionProvider soundPositionProvider;
    private final float range;

    public ServerBasicNotePlayer(Set<SongPlayerRef> players, InstrumentSoundProvider soundProvider, float volume,
                                 SoundPositionProvider soundPositionProvider, float range) {
        this.soundProvider = soundProvider;
        this.volume = clamp(volume, 0f, 1f);
        this.players = players;
        this.soundPositionProvider = soundPositionProvider;
        this.range = range;
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

        Vec3 pos = soundPositionProvider.getPosition(player, panning);

        Vec3 vPos = emulateAttenuationSoundPos(player, pos, volume, range);

        player.connection.send(new ClientboundSoundPacket(
                BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.RECORDS,
                vPos.x(), vPos.y(), vPos.z(),
                volume, vanillaPitch, player.getRandom().nextLong()
        ));
    }

    public static Vec3 emulateAttenuationSoundPos(ServerPlayer player, Vec3 soundPos, float volume, double range) {
        if (volume <= 0) return soundPos;

        Vec3 playerPos = player.getEyePosition();

        double dx = soundPos.x() - playerPos.x();
        double dy = soundPos.y() - playerPos.y();
        double dz = soundPos.z() - playerPos.z();
        double distSq = dx * dx + dy * dy + dz * dz;

        if (distSq > range * range) return soundPos;

        // Offset the virtual sound position towards the player so that the client's linear
        // attenuation over travelDist exactly reproduces the desired linear fade over 'range':
        //   client gain = volume * (1 - virtualDist / travelDist)
        //               = volume * (1 - dist / range)
        double travelDist = volume > 1 ? volume * 16.0 : 16.0;
        double factor = travelDist / range;

        return new Vec3(
                playerPos.x() + dx * factor,
                playerPos.y() + dy * factor,
                playerPos.z() + dz * factor
        );
    }

    public synchronized void addPlayer(SongPlayerRef player) {
        players.add(player);
    }

    public synchronized void removePlayer(SongPlayerRef player) {
        players.remove(player);
    }
}
