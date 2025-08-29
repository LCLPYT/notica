package work.lclpnet.notica.api.data;

import work.lclpnet.notica.impl.data.ImmutableLoopConfig;

import java.util.Optional;

public record LoopOverride(Optional<Boolean> enabled, Optional<Integer> loopCount, Optional<Integer> loopStartTick) {

    public static LoopOverride DEFAULT = new LoopOverride(Optional.empty(), Optional.empty(), Optional.empty());

    public LoopOverride withEnabled(boolean enabled) {
        return new LoopOverride(Optional.of(enabled), loopCount, loopStartTick);
    }

    public LoopOverride withStartTick(int startTick) {
        return new LoopOverride(enabled, loopCount, Optional.of(startTick));
    }

    public LoopOverride withLoopCount(int loopCount) {
        return new LoopOverride(enabled, Optional.of(loopCount), loopStartTick);
    }

    public ImmutableLoopConfig override(LoopConfig base) {
        return new ImmutableLoopConfig(
                enabled.orElseGet(base::enabled),
                loopCount.orElseGet(base::loopCount),
                loopStartTick.orElseGet(base::loopStartTick)
        );
    }
}
