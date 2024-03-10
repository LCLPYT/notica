package work.lclpnet.kibu.nbs.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
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

public class ClientBasicNotePlayer implements NotePlayer {

    private final InstrumentSoundProvider soundProvider;
    private final float volume;
    private final PlayerConfig playerConfig;

    public ClientBasicNotePlayer(InstrumentSoundProvider soundProvider, float volume, PlayerConfig playerConfig) {
        this.soundProvider = soundProvider;
        this.volume = volume;
        this.playerConfig = playerConfig;
    }

    @Override
    public void playNote(Song song, Layer layer, Note note) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
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

        float panning = ((layer.panning() + note.panning()) * 0.5f - 100) / 100;  // [-1, 1], 0=center

        var instance = new PositionedSoundInstance(sound.getId(), SoundCategory.RECORDS, volume, vanillaPitch,
                player.getRandom(), false, 0, SoundInstance.AttenuationType.NONE, panning, 0, 0, true);

        client.executeSync(() -> client.getSoundManager().play(instance));
    }
}
