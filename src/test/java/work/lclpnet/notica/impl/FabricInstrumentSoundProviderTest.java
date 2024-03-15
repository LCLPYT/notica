package work.lclpnet.notica.impl;

import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import work.lclpnet.notica.impl.data.ImmutableCustomInstrument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FabricInstrumentSoundProviderTest {

    @BeforeAll
    public static void bootstrap() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
    }

    @Test
    void getVanillaInstrument() {
        var provider = new FabricInstrumentSoundProvider(Registries.SOUND_EVENT);

        soundEquals(provider, 0,  SoundEvents.BLOCK_NOTE_BLOCK_HARP);
        soundEquals(provider, 1,  SoundEvents.BLOCK_NOTE_BLOCK_BASS);
        soundEquals(provider, 2,  SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM);
        soundEquals(provider, 3,  SoundEvents.BLOCK_NOTE_BLOCK_SNARE);
        soundEquals(provider, 4,  SoundEvents.BLOCK_NOTE_BLOCK_HAT);
        soundEquals(provider, 5,  SoundEvents.BLOCK_NOTE_BLOCK_GUITAR);
        soundEquals(provider, 6,  SoundEvents.BLOCK_NOTE_BLOCK_FLUTE);
        soundEquals(provider, 7,  SoundEvents.BLOCK_NOTE_BLOCK_BELL);
        soundEquals(provider, 8,  SoundEvents.BLOCK_NOTE_BLOCK_CHIME);
        soundEquals(provider, 9,  SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE);
        soundEquals(provider, 10, SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE);
        soundEquals(provider, 11, SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL);
        soundEquals(provider, 12, SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO);
        soundEquals(provider, 13, SoundEvents.BLOCK_NOTE_BLOCK_BIT);
        soundEquals(provider, 14, SoundEvents.BLOCK_NOTE_BLOCK_BANJO);
        soundEquals(provider, 15, SoundEvents.BLOCK_NOTE_BLOCK_PLING);
    }

    private void soundEquals(FabricInstrumentSoundProvider provider, int id, RegistryEntry.Reference<SoundEvent> ref) {
        assertEquals(ref.value(), provider.getVanillaInstrumentSound((byte) id));
    }

    @Test
    void getCustomInstrument_soundFileValid_bySoundFile() {
        var provider = new FabricInstrumentSoundProvider(Registries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("", "entity.experience_orb.pickup.ogg", (byte) 45);
        SoundEvent sound = provider.getCustomInstrumentSound(instrument);
        assertEquals(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, sound);
    }

    @Test
    void getCustomInstrument_soundFileInvalid_byName() {
        var provider = new FabricInstrumentSoundProvider(Registries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("entity.experience_orb.pickup", "bla bla", (byte) 45);
        SoundEvent sound = provider.getCustomInstrumentSound(instrument);
        assertEquals(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, sound);
    }

    @Test
    void getCustomInstrument_soundFileAndNameInvalid_null() {
        var provider = new FabricInstrumentSoundProvider(Registries.SOUND_EVENT);

        ImmutableCustomInstrument instrument = new ImmutableCustomInstrument("bla bla bla", "bla bla", (byte) 45);
        assertNull(provider.getCustomInstrumentSound(instrument));
    }
}