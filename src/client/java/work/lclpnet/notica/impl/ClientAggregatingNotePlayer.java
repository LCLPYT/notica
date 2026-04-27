package work.lclpnet.notica.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import work.lclpnet.notica.api.AggregatingPlayer;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.NoteHelper;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static work.lclpnet.notica.util.NoteHelper.normalizePanning;

public class ClientAggregatingNotePlayer implements NotePlayer, AggregatingPlayer {

    private static final int MAX_QUEUED_NOTES = 1024;

    private final InstrumentSoundProvider soundProvider;
    private final float volume;
    private final PlayerConfig playerConfig;
    private final DirectSoundManager directSoundManager;
    private final SoundPositionProvider positionProvider;
    private final boolean relativePosition;
    private final float range;
    private final List<NbsSoundInstance> notes = new ArrayList<>(16);
    private int deSyncedNotes = 0;

    public ClientAggregatingNotePlayer(InstrumentSoundProvider soundProvider, float volume, PlayerConfig playerConfig,
                                       DirectSoundManager directSoundManager, SoundPositionProvider positionProvider,
                                       boolean relativePosition, float range) {
        this.soundProvider = soundProvider;
        this.volume = volume;
        this.playerConfig = playerConfig;
        this.directSoundManager = directSoundManager;
        this.positionProvider = positionProvider;
        this.relativePosition = relativePosition;
        this.range = range;
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

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

        float openAlPitch = NoteHelper.openAlPitch((short) (key * 100 + note.pitch()));  // (0.0, any]
        float volume = layer.volume() * note.velocity() * 1e-4f * this.volume * playerConfig.getVolume();
        float panning = normalizePanning(layer.panning(), note.panning());  // [-1, 1], 0=center

        if (volume <= 0) return;

        Vec3 soundPos = positionProvider.getPosition(player, panning);

        SoundInstance.Attenuation attenuation = relativePosition
                ? SoundInstance.Attenuation.NONE
                : SoundInstance.Attenuation.LINEAR;

        // for custom sounds, find out if there is a Sound for the id (only if there is none)
        // then mixin into SoundSystem.play and allow it through
        var instance = new NbsSoundInstance(
                sound.location(),
                SoundSource.RECORDS,
                volume,
                openAlPitch,
                player.getRandom(),
                false,
                0,
                attenuation,
                soundPos.x,
                soundPos.y,
                soundPos.z,
                relativePosition,
                directSoundManager,
                range
        );

        synchronized (this) {
            if (notes.size() < MAX_QUEUED_NOTES) {
                notes.add(instance);
            }
        }
    }

    @Override
    public void finishAggregation() {
        Minecraft client = Minecraft.getInstance();
        SoundManager soundManager = client.getSoundManager();

        int count;

        synchronized (this) {
            count = max(0, notes.size() - deSyncedNotes);
            deSyncedNotes += count;
        }

        client.executeIfPossible(() -> {
            synchronized (this) {
                List<NbsSoundInstance> range = notes.subList(0, count);

                range.forEach(soundManager::play);
                range.clear();

                deSyncedNotes -= count;
            }
        });
    }
}
