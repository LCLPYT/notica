package work.lclpnet.notica.impl.data;

import work.lclpnet.notica.api.data.LoopConfig;

public record ImmutableLoopConfig(boolean enabled, int loopCount, int loopStartTick) implements LoopConfig {
    public static final ImmutableLoopConfig NONE = new ImmutableLoopConfig(false, 0, 0);
}
