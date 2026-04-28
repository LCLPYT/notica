package work.lclpnet.notica.config;

import com.electronwill.nightconfig.core.serde.annotations.SerdeComment;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
@Getter @Setter
public class NoticaClientConfig {

    @SerdeComment("Allows you to force a playback variant. By default, the playback variant is defined by the server.")
    private PlaybackVariantOverride playbackVariantOverride = PlaybackVariantOverride.USE_DEFAULT;

    @SerdeComment("Allows you to force a stereo mixing mode for pre-processed songs. By default, the mixing mode is defined by the server.")
    private StereoModeOverride stereoModeOverride = StereoModeOverride.USE_DEFAULT;

    @SerdeComment("Multiplier for the doppler effect intensity on speaker sounds.")
    @ConfigSlider(min = 0, max = 200, factor = 100)
    private double dopplerIntensity = 1.0;

    @SerdeComment("Whether to apply the listener's movement velocity to the OpenAL listener, enabling doppler effect from player motion.")
    private boolean listenerVelocity = false;
}
