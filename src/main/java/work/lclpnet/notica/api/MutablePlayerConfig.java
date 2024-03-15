package work.lclpnet.notica.api;

public interface MutablePlayerConfig {

    /**
     * Set whether the player supports extended octave range.
     * @param supported Whether the player supports extended octave range.
     */
    void setExtendedRangeSupported(boolean supported);

    /**
     * Set the player's music volume.
     * @param volume The volume, ranging [0, 1].
     */
    void setVolume(float volume);
}
