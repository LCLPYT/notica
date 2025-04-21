package work.lclpnet.notica.impl.data;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import work.lclpnet.notica.api.data.TempoChange;

import java.util.List;

import static java.lang.Math.ceil;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ImmutableSongTempoTest {

    ImmutableSongTempo tempo;

    @BeforeEach
    void setUp() {
        tempo = new ImmutableSongTempo(List.of(
                new TempoChange(0, 10),
                new TempoChange(5, 20),
                new TempoChange(8, 30),
                new TempoChange(9, 20),
                new TempoChange(15, 10)
        ));
    }

    @Test
    void tempoAt() {
        assertEquals(10, tempo.tempoAt(0));
        assertEquals(10, tempo.tempoAt(1));
        assertEquals(10, tempo.tempoAt(2));
        assertEquals(10, tempo.tempoAt(4));
        assertEquals(10, tempo.tempoAt(-1));
        assertEquals(10, tempo.tempoAt(-250));

        assertEquals(20, tempo.tempoAt(5));
        assertEquals(20, tempo.tempoAt(6));
        assertEquals(20, tempo.tempoAt(7));

        assertEquals(30, tempo.tempoAt(8));

        assertEquals(20, tempo.tempoAt(9));
        assertEquals(20, tempo.tempoAt(10));
        assertEquals(20, tempo.tempoAt(14));

        assertEquals(10, tempo.tempoAt(15));
        assertEquals(10, tempo.tempoAt(16));
        assertEquals(10, tempo.tempoAt(20));
        assertEquals(10, tempo.tempoAt(125));
        assertEquals(10, tempo.tempoAt(10_000));
    }

    @Test
    void durationSeconds_offset0_duration20() {
        float expected = 1 / 10f * 5
                + 1 / 20f * 3
                + 1 / 30f * 1
                + 1 / 20f * 6
                + 1 / 10f * 5;

        assertEquals(expected, tempo.durationSeconds(0, 20));
    }

    @Test
    void durationSeconds_offset6_duration20() {
        float expected = 1 / 20f * 2
                + 1 / 30f * 1
                + 1 / 20f * 6
                + 1 / 10f * 11;

        assertEquals(expected, tempo.durationSeconds(6, 20));
    }

    @Test
    void durationTicks_offset0_duration20ticks() {
        float durationSeconds = 1 / 10f * 5
                + 1 / 20f * 3
                + 1 / 30f * 1
                + 1 / 20f * 6
                + 1 / 10f * 5;

        assertEquals(20, tempo.durationTicks(0, durationSeconds));
    }

    @Test
    void durationTicks_offset7_duration1sec() {
        float secondsWithoutLastSegment = 1 / 20f * 1
                + 1 / 30f * 1
                + 1 / 20f * 6;

        float targetSeconds = 1.f;
        float lastSegmentTicks = (targetSeconds - secondsWithoutLastSegment) * 10f;
        int expectedTicks = 8 + (int) ceil(lastSegmentTicks);

        assertEquals(expectedTicks, tempo.durationTicks(7, targetSeconds));
    }
}