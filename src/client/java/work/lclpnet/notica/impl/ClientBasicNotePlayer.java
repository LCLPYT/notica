package work.lclpnet.notica.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.NotePlayer;
import work.lclpnet.notica.api.PlayerConfig;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.util.NoteHelper;

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

        float openAlPitch = NoteHelper.openAlPitch((short) (key * 100 + note.pitch()));  // (0.0, any]
        float volume = layer.volume() * note.velocity() * 1e-4f * this.volume * playerConfig.getVolume();
        float panning = ((layer.panning() + note.panning()) * 0.5f - 100) / 100;  // [-1, 1], 0=center

        var instance = new NbsSoundInstance(sound.getId(), SoundCategory.RECORDS, volume, openAlPitch,
                player.getRandom(), false, 0, SoundInstance.AttenuationType.NONE, panning, 0, 0, true);

        client.executeSync(() -> client.getSoundManager().play(instance));
    }
}
