package work.lclpnet.kibu.nbs.data;

public record LoopConfig(boolean enabled, int loopCount, int loopStartTick) {

    public static final LoopConfig NONE = new LoopConfig(false, 0, 0);

    public boolean infinite() {
        return loopCount == 0;
    }
}
