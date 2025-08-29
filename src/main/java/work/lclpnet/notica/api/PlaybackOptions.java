package work.lclpnet.notica.api;

import work.lclpnet.notica.api.data.LoopOverride;

/**
 * @param volume The playback volume of the song, ranges [0, 1].
 * @param playbackVariant The {@link PlaybackVariant} that determines how the song is being played.
 *                Only works for players with Notica installed.
 * @param stereoMode The {@link StereoMode} that determines how notes with stereo panning are played.
 *                   Only works if for players with Notica installed and if the playback mode is {@link PlaybackVariant#STREAMED}.
 * @param loopOverride Loop setting overrides.
 *                     By default, nothing is overridden and the loop settings from the song file are used.
 *                     Can be used, for example, to disable looping for a song.
 */
public record PlaybackOptions(
        float volume,
        PlaybackVariant playbackVariant,
        StereoMode stereoMode,
        LoopOverride loopOverride
) {

    public PlaybackOptions(float volume) {
        this(volume, PlaybackVariant.STREAMED, StereoMode.SPATIAL);
    }

    public PlaybackOptions(float volume, PlaybackVariant variant, StereoMode stereoMode) {
        this(volume, variant, stereoMode, LoopOverride.DEFAULT);
    }

    public PlaybackOptions withVolume(float volume) {
        return new PlaybackOptions(volume, playbackVariant, stereoMode, loopOverride);
    }

    public PlaybackOptions withPlaybackVariant(PlaybackVariant playbackVariant) {
        return new PlaybackOptions(volume, playbackVariant, stereoMode, loopOverride);
    }

    public PlaybackOptions withStereoMode(StereoMode stereoMode) {
        return new PlaybackOptions(volume, playbackVariant, stereoMode, loopOverride);
    }

    public PlaybackOptions withLoopOverride(LoopOverride loopOverride) {
        return new PlaybackOptions(volume, playbackVariant, stereoMode, loopOverride);
    }
}
