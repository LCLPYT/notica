package work.lclpnet.notica.api;

public enum StereoMode {

    /**
     * Perceived volume doesn't change depending on the stereo panning.
     * Interpolates smoothly between left and right channel.
     * */
    EQUAL_POWER,

    /**
     * Will play the sound on the channel where it's panned to.
     * Volume will decrease depending on the absolute panning value.
     * This mimics vanilla playback of sounds that are using OpenAL for playback.
     * The result is not equal to vanilla though.
     */
    SPATIAL
}
