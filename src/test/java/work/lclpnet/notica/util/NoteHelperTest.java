package work.lclpnet.notica.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.notica.util.NoteHelper.openAlPitch;
import static work.lclpnet.notica.util.NoteHelper.transposedPitch;

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

    @Test
    void openAlPitch_mapping_isCorrect() {
        assertEquals(0.5f, openAlPitch((short) 3300), 1e-6f);
        assertEquals(1.0f, openAlPitch((short) 4500), 1e-6f);
        assertEquals(2.0f, openAlPitch((short) 5700), 1e-6f);

        // extended range below
        assertEquals(0.25f, openAlPitch((short) 2100), 1e-6f);
        assertEquals(0.125f, openAlPitch((short) 900), 1e-6f);
        assertEquals(0.0625f, openAlPitch((short) -300), 1e-6f);

        // extended range above
        assertEquals(4.0f, openAlPitch((short) 6900), 1e-6f);
        assertEquals(8.0f, openAlPitch((short) 8100), 1e-6f);
        assertEquals(16.0f, openAlPitch((short) 9300), 1e-6f);
    }
}