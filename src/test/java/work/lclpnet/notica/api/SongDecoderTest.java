package work.lclpnet.notica.api;

import it.unimi.dsi.fastutil.ints.IntShortPair;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.CustomInstrument;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.api.data.SongMeta;
import work.lclpnet.notica.impl.data.ImmutableLoopConfig;
import work.lclpnet.notica.impl.data.ImmutableSongMeta;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class SongDecoderTest {

    @Test
    void parse() throws IOException {
        Song song = parseSong("megalovania");

        assertEquals(2302, song.durationTicks());
        assertEquals(16.0f, song.ticksPerSecond(), 10e-6f);
        assertEquals(143.875f, song.durationSeconds(), 1e-6f);
        assertEquals(4, song.signature());
        assertEquals(2304, song.paddedDurationTicks());
        assertEquals(144f, song.paddedDurationSeconds(), 1e-6f);
        assertTrue(song.stereo());

        SongMeta meta = song.metaData();
        assertEquals(new ImmutableSongMeta("Megalovania (Smash Ultimate Version)", "ShinkoNetCavy",
                "Toby Fox", ""), meta);

        assertEquals(new ImmutableLoopConfig(true, (byte) 0, (short) 0), song.loopConfig());

        var layers = song.layers();
        assertEquals(61, layers.size());

        int notesSum = layers.stream()
                .mapToInt(layer -> layer.notes().size())
                .sum();

        assertEquals(12697, notesSum);

        Instruments instruments = song.instruments();
        assertEquals(16, instruments.customBegin());
        assertEquals(3, instruments.custom().length);
    }

    @Test
    void parse_withTempoChanges() throws IOException {
        Song song = parseSong("tempo_change_test");

        CustomInstrument[] customInstruments = song.instruments().custom();

        int tempoChangerIndex = IntStream.range(0, customInstruments.length)
                .filter(i -> "Tempo Changer".equals(customInstruments[i].name()))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Tempo Changer not found in song"));

        byte tempoChangerInstrument = (byte) (tempoChangerIndex + song.instruments().customBegin());

        List<IntShortPair> tempoChanges = song.layers().stream()
                .flatMap(layer -> layer.notes().stream()
                        .filter(note -> note.instrument() == tempoChangerInstrument)
                        .flatMap(note -> Optional.of(layer.notes().index(note))
                                .filter(OptionalInt::isPresent)
                                .map(OptionalInt::getAsInt)
                                .map(time -> IntShortPair.of(time, note.pitch()))
                                .stream()))
                .toList();

        assertEquals(List.of(
                tempoChange(0, 233),
                tempoChange(8, 201),
                tempoChange(16, 164),
                tempoChange(20, 153),
                tempoChange(24, 150),
                tempoChange(48, 110),
                tempoChange(56, 308)
        ), tempoChanges);
    }

    private static IntShortPair tempoChange(int time, int bpm) {
        return IntShortPair.of(time, (short) bpm);
    }

    private @NotNull Song parseSong(String name) throws IOException {
        Song song;

        try (var in = getClass().getResourceAsStream("/songs/%s.nbs".formatted(name))) {
            assertNotNull(in);
            song = SongDecoder.parse(in, 16);
        }

        assertNotNull(song);
        return song;
    }
}