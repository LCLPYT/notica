package work.lclpnet.kibu.nbs.impl.data;

import work.lclpnet.kibu.nbs.api.data.LoopConfig;

public record ImmutableLoopConfig(boolean enabled, int loopCount, int loopStartTick) implements LoopConfig {
    public static final ImmutableLoopConfig NONE = new ImmutableLoopConfig(false, 0, 0);
}
