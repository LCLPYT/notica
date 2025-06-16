package work.lclpnet.notica.util;

import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.SongDecoder;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.impl.mix.SoundRef;
import work.lclpnet.notica.impl.mix.SoundSampleProvider;

import java.util.Optional;

public class TestSoundSampleProvider implements SoundSampleProvider {

    private final Instruments instruments;
    private final TestSoundRegistry soundRegistry;

    public TestSoundSampleProvider(Instruments instruments, TestSoundRegistry soundRegistry) {
        this.instruments = instruments;
        this.soundRegistry = soundRegistry;
    }

    @Override
    public Optional<SoundRef> getSample(byte instrument) {
        CustomInstrument custom = instruments.custom(instrument);

        if (custom != null) {
            String id = getCustomInstrumentId(custom);

            return soundRegistry.loadBySoundId(id).or(() -> loadDirect(custom));
        } else {
            String id = getVanillaInstrumentId(instrument);

            return soundRegistry.loadBySoundId(id);
        }
    }

    private Optional<SoundRef> loadDirect(CustomInstrument custom) {
        String name = custom.name();

        if (SongDecoder.TEMPO_CHANGER_NAME.equals(name)) {
            return Optional.empty();
        }

        String file = custom.soundFile();
        int sep = file.indexOf("/");
        String namespace;
        String path;

        if (sep != -1) {
            namespace = file.substring(0, sep);
            path = file.substring(sep + 1);
        } else {
            sep = name.indexOf("/");

            if (sep != -1) {
                namespace = name.substring(0, sep);
                path = name.substring(sep + 1);
            } else {
                namespace = "minecraft";
                path = file;  // <- intentionally file
            }
        }

        String assetPath = namespace + "/sounds/" + path;

        return Optional.of(soundRegistry.createRef(assetPath, 1f, 1f));
    }

    private static @Nullable String getVanillaInstrumentId(byte instrument) {
        return switch (instrument) {
            case 0  -> "block.note_block.harp";
            case 1  -> "block.note_block.bass";
            case 2  -> "block.note_block.basedrum";
            case 3  -> "block.note_block.snare";
            case 4  -> "block.note_block.hat";
            case 5  -> "block.note_block.guitar";
            case 6  -> "block.note_block.flute";
            case 7  -> "block.note_block.bell";
            case 8  -> "block.note_block.chime";
            case 9  -> "block.note_block.xylophone";
            case 10 -> "block.note_block.iron_xylophone";
            case 11 -> "block.note_block.cow_bell";
            case 12 -> "block.note_block.didgeridoo";
            case 13 -> "block.note_block.bit";
            case 14 -> "block.note_block.banjo";
            case 15 -> "block.note_block.pling";
            default -> null;
        };
    }

    private @Nullable String getCustomInstrumentId(CustomInstrument instrument) {
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
            return "block.note_block.pling";
        }

        // try to parse filename as sound id
        if (soundRegistry.has(file)) {
            return file;
        }

        // try the sound name instead
        if (soundRegistry.has(name)) {
            return name;
        }

        return file;  // check if asset for file or name exists?
    }
}
