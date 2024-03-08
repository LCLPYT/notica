package work.lclpnet.kibu.nbs.util;

public class NoteHelper {

    private static final byte
            LOWEST_VANILLA_KEY = 33,
            HIGHEST_VANILLA_KEY = 57,
            OCTAVE_KEYS = 12,
            TWO_OCTAVES_KEYS = OCTAVE_KEYS * 2;
    private static final short
            LOWEST_VANILLA_PITCH = LOWEST_VANILLA_KEY * 100,
            HIGHEST_VANILLA_PITCH = HIGHEST_VANILLA_KEY * 100,
            OCTAVE_PITCH = OCTAVE_KEYS * 100;

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
        key += (byte) (pitch / 100);

        while (key < LOWEST_VANILLA_KEY) key += OCTAVE_KEYS;
        while (key > HIGHEST_VANILLA_KEY) key -= OCTAVE_KEYS;

        key -= LOWEST_VANILLA_KEY;

        // convert to vanilla pitch value [0.5, 2]
        return vanillaPitch((short) (key * 100 + pitch % 100));
    }

    /**
     * Get the vanilla pitch of the given note key and pitch (detune), normalized to the respective octave range.
     *
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return The vanilla pitch [0.5, 2.0].
     */
    public static float normalizedPitch(byte key, short pitch) {
        key += (byte) (pitch / 100);

        final short range = (short) Math.floor((double) (key - LOWEST_VANILLA_KEY) / TWO_OCTAVES_KEYS);
        key -= (byte) (33 + range * 24);

        // convert to vanilla pitch value [0.5, 2]
        return vanillaPitch((short) (key * 100 + pitch % 100));
    }

    private static float vanillaPitch(short pitch) {
        float vanilla = (float) Math.pow(2, (double) (pitch - OCTAVE_PITCH) / OCTAVE_PITCH);
        return Math.max(0f, Math.min(2f, vanilla));
    }

    /**
     * Check if a given note key and pitch (detune) is within the vanilla key range [33, 57].
     * @param key The note key, from [0, 87]. 0 is A0 and 87 is C8.
     * @param pitch The fine pitch of the note. Most of the time within [-1200, 1200]. 0 is no fine-tuning, 100 cents is one semitone difference.
     * @return Whether the note key and pitch is playable by a vanilla client.
     */
    public static boolean isOutsideVanillaRange(byte key, short pitch) {
        key += (byte) (pitch / 100);

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
        key += (byte) (pitch / 100);

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
