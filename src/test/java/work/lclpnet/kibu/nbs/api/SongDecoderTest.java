package work.lclpnet.kibu.nbs.api;

import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.nbs.data.*;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class SongDecoderTest {

    @Test
    void parse() throws IOException {
        Song song;

        try (var in = getClass().getResourceAsStream("/songs/megalovania.nbs")) {
            assertNotNull(in);
            song = SongDecoder.parse(in, 16);
        }

        assertNotNull(song);
        assertEquals(2302, song.durationTicks());
        assertEquals(16.0f, song.ticksPerSecond(), 10e-6f);
        assertEquals(143.875f, song.durationSeconds(), 1e-6f);

        SongMeta meta = song.metaData();
        assertEquals(new SongMeta("Megalovania (Smash Ultimate Version)", "ShinkoNetCavy",
                "Toby Fox", ""), meta);

        assertEquals(new LoopConfig(true, (byte) 0, (short) 0), song.loopConfig());

        var layers = song.layers();
        assertEquals(61, layers.size());

        int notesSum = layers.values().stream()
                .mapToInt(layer -> layer.notes().size())
                .sum();

        assertEquals(12697, notesSum);

        Instruments instruments = song.instruments();
        assertEquals(16, instruments.customBegin());
        assertEquals(3, instruments.custom().length);
    }
}