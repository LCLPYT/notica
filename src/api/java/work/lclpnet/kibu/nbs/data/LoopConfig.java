package work.lclpnet.kibu.nbs.data;

public record LoopConfig(boolean enabled, byte loopCount, short loopStartTick) {

    public boolean infinite() {
        return loopCount == 0;
    }
}
