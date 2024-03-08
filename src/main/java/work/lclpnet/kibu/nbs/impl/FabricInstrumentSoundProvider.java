package work.lclpnet.kibu.nbs.impl;

import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.kibu.nbs.api.InstrumentSoundProvider;
import work.lclpnet.kibu.nbs.data.CustomInstrument;
import work.lclpnet.kibu.nbs.mixin.SoundEventAccessor;
import work.lclpnet.kibu.nbs.util.NoteHelper;

import java.util.HashMap;
import java.util.Map;

public class FabricInstrumentSoundProvider implements InstrumentSoundProvider {

    private final Registry<SoundEvent> soundRegistry;
    private final Map<CustomInstrument, SoundEvent> cache = new HashMap<>();
    private final Map<String, SoundEvent> extended = new HashMap<>();

    public FabricInstrumentSoundProvider(MinecraftServer server) {
        this(server.getRegistryManager());
    }

    public FabricInstrumentSoundProvider(DynamicRegistryManager registryManager) {
        this(registryManager.get(RegistryKeys.SOUND_EVENT));
    }

    public FabricInstrumentSoundProvider(Registry<SoundEvent> soundRegistry) {
        this.soundRegistry = soundRegistry;
    }

    @Override
    @Nullable
    public SoundEvent getVanillaInstrumentSound(byte instrument) {
        return switch (instrument) {
            case 0  -> SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();
            case 1  -> SoundEvents.BLOCK_NOTE_BLOCK_BASS.value();
            case 2  -> SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value();
            case 3  -> SoundEvents.BLOCK_NOTE_BLOCK_SNARE.value();
            case 4  -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value();
            case 5  -> SoundEvents.BLOCK_NOTE_BLOCK_GUITAR.value();
            case 6  -> SoundEvents.BLOCK_NOTE_BLOCK_FLUTE.value();
            case 7  -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value();
            case 8  -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
            case 9  -> SoundEvents.BLOCK_NOTE_BLOCK_XYLOPHONE.value();
            case 10 -> SoundEvents.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE.value();
            case 11 -> SoundEvents.BLOCK_NOTE_BLOCK_COW_BELL.value();
            case 12 -> SoundEvents.BLOCK_NOTE_BLOCK_DIDGERIDOO.value();
            case 13 -> SoundEvents.BLOCK_NOTE_BLOCK_BIT.value();
            case 14 -> SoundEvents.BLOCK_NOTE_BLOCK_BANJO.value();
            case 15 -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            default -> null;
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
        String name = NoteHelper.getExtendedSoundName(sound.getId().toString(), key, pitch);
        SoundEvent extendedSound = this.extended.get(name);

        if (extendedSound != null) {
            return extendedSound;
        }

        // create a new sound event
        Identifier id = new Identifier(name);

        SoundEventAccessor access = (SoundEventAccessor) sound;

        if (access.isStaticDistance()) {
            extendedSound = SoundEvent.of(id, access.getDistanceToTravel());
        } else {
            extendedSound = SoundEvent.of(id);
        }

        this.extended.put(name, extendedSound);

        return extendedSound;
    }

    @Nullable
    private SoundEvent fetchCustomSound(CustomInstrument instrument) {
        String file = instrument.soundFile();

        if (file.endsWith(".ogg")) {
            file = file.substring(0, file.length() - 4);
        }

        // support for old nbs files that encoded the pling sound as custom instrument
        if (file.equalsIgnoreCase("pling")) {
            return SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
        }

        // try to parse filename as sound id
        Identifier idFromFile = Identifier.tryParse(file);

        if (idFromFile != null) {
            SoundEvent sound = soundRegistry.get(idFromFile);

            if (sound != null) {
                return sound;
            }
        }

        // try the sound name instead
        Identifier idFromName = Identifier.tryParse(instrument.name());

        if (idFromName != null) {
            SoundEvent sound = soundRegistry.get(idFromName);

            if (sound != null) {
                return sound;
            }
        }

        // fallback for non-vanilla custom sounds
        if (idFromFile != null) {
            return SoundEvent.of(idFromFile);
        }

        if (idFromName != null) {
            return SoundEvent.of(idFromName);
        }

        return null;
    }
}
