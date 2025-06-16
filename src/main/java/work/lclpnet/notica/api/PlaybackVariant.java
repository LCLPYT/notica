package work.lclpnet.notica.api;

public enum PlaybackVariant {

    /// Pre-processes the next few seconds of the song and plays it as continuously streamed sound, similar to
    /// vanilla background music or records.
    ///
    /// This playback variant has no upper limit for concurrently playing individual note sounds.
    /// Also, other game sounds are not cut off when the song is playing a lot of notes concurrently.
    /// Definitely use this playback variant if the song has a lot of concurrent sounds (>150 sounds simultaneously).
    ///
    /// Note timings are most precise with this playback mode (accurate to 1/48000 of a second).
    ///
    /// With this playback variant, different {@link StereoMode}s can be selected.
    ///
    /// Please note that the pre-processing is computationally expensive.
    /// For people with very weak CPUs, it might not be possible to achieve real time playback.
    /// If that is the case, those people have the option to disable this playback variant in the mod settings.
    ///
    /// Only works for players that have Notica installed on their client; other players will get individual note
    /// playback nonetheless.
    STREAMED,

    /// Plays every note sound individually.
    ///
    /// This has an upper limit of simultaneously playing note sounds.
    /// The maximum depends on a systems audio device, audio driver, operating system and OpenAL soft configuration.
    /// If that upper limit is reached, new sounds are skipped, resulting in missing notes.
    /// This also silences other sounds of the game, as they also count into the limit.
    ///
    /// Timing accuracy depends on whether a player has Notica installed or not.
    /// If the player doesn't have Notica, the timing of notes is subject to their network latency and client tick times.
    /// A player with Notica installed has better timing accuracy, but is still limited to the client tick times.
    ///
    /// Works on servers for players without Notica installed and also on clients that have Notica installed.
    INDIVIDUAL
}
