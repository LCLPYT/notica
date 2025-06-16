package work.lclpnet.notica.api;

/**
 * @param volume The playback volume of the song, ranges [0, 1].
 * @param variant The {@link PlaybackVariant} that determines how the song is being played.
 *                Only works for players with Notica installed.
 * @param stereoMode The {@link StereoMode} that determines how notes with stereo panning are played.
 *                   Only works if for players with Notica installed and if the playback mode is {@link PlaybackVariant#STREAMED}.
 */
public record PlaybackOptions(float volume, PlaybackVariant variant, StereoMode stereoMode) {

    public PlaybackOptions(float volume) {
        this(volume, PlaybackVariant.STREAMED, StereoMode.SPATIAL);
    }
}
