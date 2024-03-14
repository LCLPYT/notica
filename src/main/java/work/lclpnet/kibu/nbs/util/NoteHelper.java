package work.lclpnet.kibu.nbs.util;

public class NoteHelper {

    public static final byte
            LOWEST_VANILLA_KEY = 33,
            HIGHEST_VANILLA_KEY = 57,
            OCTAVE_KEYS = 12,
            TWO_OCTAVES_KEYS = OCTAVE_KEYS * 2;
    public static final short
            KEY_PITCH_FACTOR = 100,
            OCTAVE_PITCH = OCTAVE_KEYS * KEY_PITCH_FACTOR;

    private NoteHelper() {}

    /**
     * Get the vanilla pitch of the given note key and pitch (detune).
     * If the pitch is outside the vanilla key range [33, 57], it is transposed as many octaves as needed.
     *
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return The vanilla pitch [0.5, 2.0], where the key is transposed to the range [33, 57] if needed.
     */
    public static float transposedPitch(byte key, short pitch) {
        key += (byte) (pitch / KEY_PITCH_FACTOR);

        while (key < LOWEST_VANILLA_KEY) key += OCTAVE_KEYS;
        while (key > HIGHEST_VANILLA_KEY) key -= OCTAVE_KEYS;

        // convert to vanilla pitch value [0.5, 2]
        return clampPitch((short) (key * KEY_PITCH_FACTOR + pitch % KEY_PITCH_FACTOR));
    }

    /**
     * Get the vanilla pitch of the given note key and pitch (detune), normalized to the respective octave range.
     *
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return The vanilla pitch [0.5, 2.0].
     */
    public static float normalizedPitch(byte key, short pitch) {
        key += (byte) (pitch / KEY_PITCH_FACTOR);

        final short range = (short) Math.floor((double) (key - LOWEST_VANILLA_KEY) / TWO_OCTAVES_KEYS);
        key -= (byte) (range * 2 * OCTAVE_KEYS);

        // convert to vanilla pitch value [0.5, 2]
        return clampPitch((short) (key * KEY_PITCH_FACTOR + pitch % KEY_PITCH_FACTOR));
    }

    private static float clampPitch(short pitch) {
        float alPitch = openAlPitch(pitch);
        return Math.max(0.5f, Math.min(2f, alPitch));
    }

    public static float openAlPitch(short pitch) {
        // normalize, so that the lowest vanilla key pitch is mapped to 0
        pitch -= LOWEST_VANILLA_KEY * KEY_PITCH_FACTOR;

        // in openal, a reduction by 50% is equal to -12 semitones and increase by 50% is equal to 12 semitones
        // https://www.openal.org/documentation/openal-1.1-specification.pdf (page 38)

        // lowest vanilla pitch should map to 0.5, highest (2 octaves or 24 semitones above) should map to 2.0
        return (float) Math.pow(2, (double) (pitch - OCTAVE_PITCH) / OCTAVE_PITCH);
    }

    /**
     * Check if a given note key and pitch (detune) is within the vanilla key range [33, 57].
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return Whether the note key and pitch is playable by a vanilla client.
     */
    public static boolean isOutsideVanillaRange(byte key, short pitch) {
        key += (byte) (pitch / KEY_PITCH_FACTOR);

        return key < LOWEST_VANILLA_KEY || key > HIGHEST_VANILLA_KEY;
    }

    /**
     * OpenNBS offers a resource pack with extended octave range sounds for all vanilla instruments.
     * This method returns the name of the extended sound event.
     * @param baseName The name of the base sound event (of the vanilla octave range).
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return The base name, suffixed by _shift, where shift is the amount of octaves shifted, divided by two.
     */
    public static String getExtendedSoundName(String baseName, byte key, short pitch) {
        key += (byte) (pitch / KEY_PITCH_FACTOR);

        int range;

        if (key < LOWEST_VANILLA_KEY) {
            range = -1 * (int) Math.ceil((double) (LOWEST_VANILLA_KEY - key) / TWO_OCTAVES_KEYS);
        } else if (key > HIGHEST_VANILLA_KEY) {
            range = (int) Math.ceil((double) (key - HIGHEST_VANILLA_KEY) / TWO_OCTAVES_KEYS);
        } else {
            return baseName;
        }

        return baseName + "_" + range;
    }
}
