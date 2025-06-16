package work.lclpnet.notica.config;

import com.electronwill.nightconfig.core.serde.annotations.SerdeComment;
import org.jetbrains.annotations.Nullable;
import work.lclpnet.notica.api.PlaybackVariant;

public enum PlaybackVariantOverride {

    @SerdeComment("Recommended. Uses the default playback variant as defined by the server.")
    USE_DEFAULT,

    @SerdeComment("Always play notes individually and don't pre-process songs. Use this if you are experiencing problems with the default behaviour.")
    INDIVIDUAL,

    @SerdeComment("Always pre-process songs and never play notes individually.")
    STREAMED;

    public @Nullable PlaybackVariant variant() {
        return switch (this) {
            case USE_DEFAULT -> null;
            case INDIVIDUAL -> PlaybackVariant.INDIVIDUAL;
            case STREAMED -> PlaybackVariant.STREAMED;
        };
    }
}
