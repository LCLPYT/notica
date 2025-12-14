package work.lclpnet.notica.impl;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.InstrumentSoundProvider;
import work.lclpnet.notica.api.SongDecoder;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.util.NoteHelper;

import java.util.HashMap;
import java.util.Map;

public class FabricInstrumentSoundProvider implements InstrumentSoundProvider {

    public static final int
            HARP = 0,
            BASS = 1,
            BASEDRUM = 2,
            SNARE = 3,
            HAT = 4,
            GUITAR = 5,
            FLUTE = 6,
            BELL = 7,
            CHIME = 8,
            XYLOPHONE = 9,
            IRON_XYLOPHONE = 10,
            COW_BELL = 11,
            DIDGERIDOO = 12,
            BIT = 13,
            BANJO = 14,
            PLING = 15;

    private final Registry<SoundEvent> soundRegistry;
    private final Map<CustomInstrument, SoundEvent> cache = new HashMap<>();
    private final Map<String, SoundEvent> extended = new HashMap<>();

    public FabricInstrumentSoundProvider(MinecraftServer server) {
        this(server.registryAccess());
    }

    public FabricInstrumentSoundProvider(RegistryAccess registryManager) {
        this(registryManager.lookupOrThrow(Registries.SOUND_EVENT));
    }

    public FabricInstrumentSoundProvider(Registry<SoundEvent> soundRegistry) {
        this.soundRegistry = soundRegistry;
    }

    @Override
    @Nullable
    public SoundEvent getVanillaInstrumentSound(byte instrument) {
        return switch (instrument) {
            case HARP           -> SoundEvents.NOTE_BLOCK_HARP.value();
            case BASS           -> SoundEvents.NOTE_BLOCK_BASS.value();
            case BASEDRUM       -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
            case SNARE          -> SoundEvents.NOTE_BLOCK_SNARE.value();
            case HAT            -> SoundEvents.NOTE_BLOCK_HAT.value();
            case GUITAR         -> SoundEvents.NOTE_BLOCK_GUITAR.value();
            case FLUTE          -> SoundEvents.NOTE_BLOCK_FLUTE.value();
            case BELL           -> SoundEvents.NOTE_BLOCK_BELL.value();
            case CHIME          -> SoundEvents.NOTE_BLOCK_CHIME.value();
            case XYLOPHONE      -> SoundEvents.NOTE_BLOCK_XYLOPHONE.value();
            case IRON_XYLOPHONE -> SoundEvents.NOTE_BLOCK_IRON_XYLOPHONE.value();
            case COW_BELL       -> SoundEvents.NOTE_BLOCK_COW_BELL.value();
            case DIDGERIDOO     -> SoundEvents.NOTE_BLOCK_DIDGERIDOO.value();
            case BIT            -> SoundEvents.NOTE_BLOCK_BIT.value();
            case BANJO          -> SoundEvents.NOTE_BLOCK_BANJO.value();
            case PLING          -> SoundEvents.NOTE_BLOCK_PLING.value();
            default             -> null;
        };
    }

    @Override
    @Nullable
    public SoundEvent getCustomInstrumentSound(CustomInstrument instrument) {
        SoundEvent sound = cache.get(instrument);

        if (sound != null) {
            return sound;
        }

        sound = fetchCustomSound(instrument);

        if (sound != null) {
            cache.put(instrument, sound);
        }

        return sound;
    }

    @NotNull
    @Override
    public SoundEvent getExtendedSound(final @NotNull SoundEvent sound, byte key, short pitch) {
        String name = NoteHelper.getExtendedSoundName(sound.location().toString(), key, pitch);
        SoundEvent extendedSound = this.extended.get(name);

        if (extendedSound != null) {
            return extendedSound;
        }

        // create a new sound event
        Identifier id = Identifier.parse(name);

        extendedSound = sound.fixedRange()
                .map(fixedRanged -> SoundEvent.createFixedRangeEvent(id, fixedRanged))
                .orElseGet(() -> SoundEvent.createVariableRangeEvent(id));

        this.extended.put(name, extendedSound);

        return extendedSound;
    }

    @Nullable
    private SoundEvent fetchCustomSound(CustomInstrument instrument) {
        String name = instrument.name();

        if (SongDecoder.TEMPO_CHANGER_NAME.equals(name)) {
            return null;
        }

        String file = instrument.soundFile();

        if (file.endsWith(".ogg")) {
            file = file.substring(0, file.length() - 4);
        }

        // support for old nbs files that encoded the pling sound as custom instrument
        if (file.equalsIgnoreCase("pling")) {
            return SoundEvents.NOTE_BLOCK_PLING.value();
        }

        // try to parse filename as sound id
        Identifier idFromFile = Identifier.tryParse(file);

        if (idFromFile != null) {
            SoundEvent sound = soundRegistry.getValue(idFromFile);

            if (sound != null) {
                return sound;
            }
        }

        // try the sound name instead
        Identifier idFromName = Identifier.tryParse(name);

        if (idFromName != null) {
            SoundEvent sound = soundRegistry.getValue(idFromName);

            if (sound != null) {
                return sound;
            }
        }

        // fallback for non-vanilla custom sounds
        if (idFromFile != null) {
            return SoundEvent.createVariableRangeEvent(idFromFile);
        }

        if (idFromName != null) {
            return SoundEvent.createVariableRangeEvent(idFromName);
        }

        return null;
    }
}
