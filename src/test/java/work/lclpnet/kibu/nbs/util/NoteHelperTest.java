package work.lclpnet.kibu.nbs.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.kibu.nbs.util.NoteHelper.transposedPitch;

class NoteHelperTest {

    @Test
    void transposedPitch_inVanillaRange_noTranspose() {
        assertEquals(0.5f, transposedPitch((byte) 33, (short) 0), 1e-6f);
        assertEquals(0.707f, transposedPitch((byte) 39, (short) 0), 1e-3f);
        assertEquals(1f, transposedPitch((byte) 45, (short) 0), 1e-6f);
        assertEquals(Math.sqrt(2), transposedPitch((byte) 51, (short) 0), 1e-3f);
        assertEquals(2f, transposedPitch((byte) 57, (short) 0), 1e-6f);
    }

    @Test
    void transposedPitch_outOfVanillaRange_octaveTransposed() {
        assertEquals(0.5f, transposedPitch((byte) 33, (short) 0), 1e-6f);
        assertEquals(0.5f, transposedPitch((byte) 21, (short) 0), 1e-6f);
        assertEquals(0.5f, transposedPitch((byte) 9, (short) 0), 1e-6f);

        assertEquals(2f, transposedPitch((byte) 57, (short) 0), 1e-6f);
        assertEquals(2f, transposedPitch((byte) 69, (short) 0), 1e-6f);
        assertEquals(2f, transposedPitch((byte) 81, (short) 0), 1e-6f);
    }
}