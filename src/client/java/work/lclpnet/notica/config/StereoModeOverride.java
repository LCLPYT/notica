package work.lclpnet.notica.config;

import com.electronwill.nightconfig.core.serde.annotations.SerdeComment;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.StereoMode;

public enum StereoModeOverride {

    @SerdeComment("Recommended. Uses the default stereo mixing mode as defined by the server.")
    USE_DEFAULT,

    @SerdeComment("Plays stereo sounds only on the channel where they are panned to. Tries to mimic vanilla result.")
    SPATIAL,

    @SerdeComment("Smoothly shift stereo sounds between both channels. Sounds will always have the perceived volume.")
    EQUAL_POWER;

    public @Nullable StereoMode stereoMode() {
        return switch (this) {
            case USE_DEFAULT -> null;
            case SPATIAL -> StereoMode.SPATIAL;
            case EQUAL_POWER -> StereoMode.EQUAL_POWER;
        };
    }
}
