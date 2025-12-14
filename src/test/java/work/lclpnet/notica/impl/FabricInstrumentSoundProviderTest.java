package work.lclpnet.notica.impl;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.lclpnet.notica.impl.data.ImmutableCustomInstrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricInstrumentSoundProviderTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void getVanillaInstrument() {
        var provider = new FabricInstrumentSoundProvider(BuiltInRegistries.SOUND_EVENT);

        soundEquals(provider, 0,  SoundEvents.NOTE_BLOCK_HARP);
        soundEquals(provider, 1,  SoundEvents.NOTE_BLOCK_BASS);
        soundEquals(provider, 2,  SoundEvents.NOTE_BLOCK_BASEDRUM);
        soundEquals(provider, 3,  SoundEvents.NOTE_BLOCK_SNARE);
        soundEquals(provider, 4,  SoundEvents.NOTE_BLOCK_HAT);
        soundEquals(provider, 5,  SoundEvents.NOTE_BLOCK_GUITAR);
        soundEquals(provider, 6,  SoundEvents.NOTE_BLOCK_FLUTE);
        soundEquals(provider, 7,  SoundEvents.NOTE_BLOCK_BELL);
        soundEquals(provider, 8,  SoundEvents.NOTE_BLOCK_CHIME);
        soundEquals(provider, 9,  SoundEvents.NOTE_BLOCK_XYLOPHONE);
        soundEquals(provider, 10, SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE);
        soundEquals(provider, 11, SoundEvents.NOTE_BLOCK_COW_BELL);
        soundEquals(provider, 12, SoundEvents.NOTE_BLOCK_DIDGERIDOO);
        soundEquals(provider, 13, SoundEvents.NOTE_BLOCK_BIT);
        soundEquals(provider, 14, SoundEvents.NOTE_BLOCK_BANJO);
        soundEquals(provider, 15, SoundEvents.NOTE_BLOCK_PLING);
    }

    private void soundEquals(FabricInstrumentSoundProvider provider, int id, Holder.Reference<SoundEvent> ref) {
        assertEquals(ref.value(), provider.getVanillaInstrumentSound((byte) id));
    }

    @Test
    void getCustomInstrument_soundFileValid_bySoundFile() {
        var provider = new FabricInstrumentSoundProvider(BuiltInRegistries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("", "entity.experience_orb.pickup.ogg", (byte) 45);
        SoundEvent sound = provider.getCustomInstrumentSound(instrument);
        assertEquals(SoundEvents.EXPERIENCE_ORB_PICKUP, sound);
    }

    @Test
    void getCustomInstrument_soundFileInvalid_byName() {
        var provider = new FabricInstrumentSoundProvider(BuiltInRegistries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("entity.experience_orb.pickup", "bla bla", (byte) 45);
        SoundEvent sound = provider.getCustomInstrumentSound(instrument);
        assertEquals(SoundEvents.EXPERIENCE_ORB_PICKUP, sound);
    }

    @Test
    void getCustomInstrument_soundFileAndNameInvalid_null() {
        var provider = new FabricInstrumentSoundProvider(BuiltInRegistries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("bla bla bla", "bla bla", (byte) 45);
        assertNull(provider.getCustomInstrumentSound(instrument));
    }
}