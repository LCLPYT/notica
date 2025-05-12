package work.lclpnet.notica.impl;

public interface TimeNoiseSampler {
    TimeNoiseSampler NONE = () -> 0;

    int sampleNoiseTime();
}
