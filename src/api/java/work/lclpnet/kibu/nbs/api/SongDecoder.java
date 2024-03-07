package work.lclpnet.kibu.nbs.api;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.kibu.nbs.data.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static work.lclpnet.kibu.nbs.api.IoHelper.*;

public class SongDecoder {

    public static final int VANILLA_INSTRUMENT_COUNT_1_14 = 16;

    private SongDecoder() {}

    /**
     * Parse a song from an {@link InputStream}.
     * Assumes game version 1.14 or higher.
     * @param input Any {@link InputStream}.
     * @return The parsed song.
     * @throws IOException If there was an IO error.
     * @see <a href="https://opennbs.org/nbs">OpenNBS Specification</a>
     */
    public static Song parse(InputStream input) throws IOException {
        return parse(input, VANILLA_INSTRUMENT_COUNT_1_14);
    }

    /**
     * Parse a song from an {@link InputStream}.
     * @param input Any {@link InputStream}.
     * @param vanillaInstrumentCount The amount of instruments in the current game version.
     * @return The parsed song.
     * @throws IOException If there was an IO error.
     * @see <a href="https://opennbs.org/nbs">OpenNBS Specification</a>
     */
    public static Song parse(InputStream input, final int vanillaInstrumentCount) throws IOException {
        DataInputStream in = new DataInputStream(input);

        // HEADER
        short durationTicks = readShortLE(in);

        final byte songVanillaInstrumentCount, version, customInstrumentOffset;

        if (durationTicks == 0) {
            // new format
            version = in.readByte();

            songVanillaInstrumentCount = in.readByte();
            customInstrumentOffset = (byte) (vanillaInstrumentCount - songVanillaInstrumentCount);

            // in version 3, length was re-added
            if (version >= 3) {
                durationTicks = readShortLE(in);
            }
        } else {
            version = 0;
            songVanillaInstrumentCount = 10;
            customInstrumentOffset = 0;
        }

        final short layerCount = readShortLE(in);

        SongMeta meta = readMetaData(in);

        float ticksPerSecond = readShortLE(in) / 100f;

        // unused
        in.readBoolean();   // auto save
        in.readByte();      // auto save interval
        in.readByte();      // time signature
        readIntLE(in);      // minutes spent
        readIntLE(in);      // left clicks
        readIntLE(in);      // right clicks
        readIntLE(in);      // note blocks added
        readIntLE(in);      // note blocks removed
        readString(in);     // midi/schematic file name

        LoopConfig loopConfig = readLoopConfig(version, in);

        // NOTE BLOCKS
        final Map<Integer, Map<Integer, Note>> layerNotes = new HashMap<>(layerCount);
        boolean stereo = false;
        short tick = -1;

        // iterate ticks
        while (true) {
            // determine next tick
            short jump = readShortLE(in);
            if (jump == 0) break;  // ticks end

            tick += jump;

            short layer = -1;

            // iterate layers
            while (true) {
                // determine layer
                jump = readShortLE(in);
                if (jump == 0) break;  // layers end

                layer += jump;

                byte instrument = in.readByte();

                // support nbs files from versions with fewer vanilla instruments
                // modern vanilla instruments are encoded as custom instruments;
                // e.g. songVanillaCount= 10, instrument=12 (didgeridoo), instrument_shifted=18 (custom instrument)
                if (customInstrumentOffset > 0 && instrument >= songVanillaInstrumentCount) {
                    instrument += customInstrumentOffset;
                }

                byte key = in.readByte();
                byte velocity;
                short panning, pitch;

                if (version >= 4) {
                    velocity = in.readByte();

                    // panning: [0..200] 0=left, 100=center, 200=right
                    panning = (short) (200 - in.readUnsignedByte());

                    if (panning != 100) {
                        stereo = true;
                    }

                    pitch = readShortLE(in);
                } else {
                    velocity = 100;
                    panning = 100;
                    pitch = 0;
                }

                var notes = layerNotes.computeIfAbsent((int) layer, i -> new HashMap<>());
                Note note = new Note(instrument, key, velocity, panning, pitch);
                notes.put((int) tick, note);
            }
        }

        // length support for version 1 and 2
        durationTicks = switch (version) {
            case 1, 2 -> tick;
            default -> durationTicks;
        };

        // LAYERS
        var layerResult = readLayers(layerCount, layerNotes, in, version);
        stereo |= layerResult.stereo();

        // CUSTOM INSTRUMENTS
        Instruments instruments = readInstruments(in, customInstrumentOffset, songVanillaInstrumentCount);

        return new Song(durationTicks, ticksPerSecond, meta, loopConfig, layerResult.layers(), instruments, stereo);
    }

    @NotNull
    private static LayerResult readLayers(short layerCount, Map<Integer, Map<Integer, Note>> layerNotes, DataInputStream in, byte version) throws IOException {
        Map<Integer, Layer> layers = new HashMap<>(layerCount);
        boolean stereo = false;

        for (int i = 0; i < layerCount; i++) {
            var notes = layerNotes.get(i);

            String name = readString(in);

            if (version >= 4) {
                in.readByte();  // locked (unused)
            }

            byte volume = in.readByte();
            short panning;

            if (version >= 2) {
                // panning: [0..200] 0=left, 100=center, 200=right
                panning = (short) (200 - in.readUnsignedByte());

                if (panning != 100) {
                    stereo = true;
                }
            } else {
                panning = 100;
            }

            if (notes == null) continue;

            layers.put(i, new Layer(name, volume, panning, notes));
        }

        return new LayerResult(layers, stereo);
    }

    @NotNull
    private static Instruments readInstruments(DataInputStream in, byte customInstrumentOffset, byte songVanillaInstrumentCount) throws IOException {
        final byte customInstrumentCount = in.readByte();

        CustomInstrument[] customInstruments = new CustomInstrument[customInstrumentCount];

        for (int i = 0; i < customInstrumentCount; i++) {
            String name = readString(in);
            String file = readString(in);
            byte key = in.readByte();

            customInstruments[i] = new CustomInstrument(name, file, key);

            in.readByte();  // press piano key (unused)
        }

        if (customInstrumentOffset < 0) {
            // outdated game version (not supported)
            throw new IOException("Tried to load song for a later game version");
        }

        byte customBegin = (byte) (songVanillaInstrumentCount + customInstrumentOffset);

        return new Instruments(customInstruments, customBegin);
    }

    @NotNull
    private static LoopConfig readLoopConfig(byte version, DataInputStream in) throws IOException {
        if (version < 4) {
            return new LoopConfig(false, (byte) 0, (short) 0);
        }

        boolean loopEnabled = in.readByte() == 1;
        byte loopCount = in.readByte();
        short loopStartTick = readShortLE(in);

        return new LoopConfig(loopEnabled, loopCount, loopStartTick);
    }

    @NotNull
    private static SongMeta readMetaData(DataInputStream in) throws IOException {
        String name = readString(in);
        String author = readString(in);
        String originalAuthor = readString(in);
        String description = readString(in);
        return new SongMeta(name, author, originalAuthor, description);
    }

    private record LayerResult(Map<Integer, Layer> layers, boolean stereo) {}
}
