package work.lclpnet.kibu.nbs.util;

public class NoteHelper {

    private static final byte
            LOWEST_VANILLA_KEY = 33,
            HIGHEST_VANILLA_KEY = 57,
            OCTAVE_KEYS = 12;
    private static final short
            LOWEST_VANILLA_PITCH = LOWEST_VANILLA_KEY * 100,
            HIGHEST_VANILLA_PITCH = HIGHEST_VANILLA_KEY * 100,
            OCTAVE_PITCH = OCTAVE_KEYS * 100;

    private NoteHelper() {}

    public static float transposedPitch(byte key, short pitch) {
        pitch += (short) (key * 100);

        while (pitch < LOWEST_VANILLA_PITCH) pitch += OCTAVE_PITCH;
        while (pitch > HIGHEST_VANILLA_PITCH) pitch -= OCTAVE_PITCH;

        pitch -= LOWEST_VANILLA_PITCH;

        // convert to vanilla pitch value [0.5, 2]
        return (float) Math.pow(2, (double) (pitch - OCTAVE_PITCH) / OCTAVE_PITCH);
    }
}
