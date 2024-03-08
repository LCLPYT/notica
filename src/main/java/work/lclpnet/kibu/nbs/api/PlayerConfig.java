package work.lclpnet.kibu.nbs.api;

public interface PlayerConfig {

    /**
     * Check if the player supports extended octave range.
     * @return Whether extended octave range is supported for this player.
     */
    boolean isExtendedRangeSupported();

    /**
     * Get the player's music volume.
     * @return The volume, ranging [0, 1]
     */
    float getVolume();
}
