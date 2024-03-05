package work.lclpnet.kibu.nbs.data;

import java.util.Map;

public record Song(int durationTicks, float ticksPerSecond, SongMeta metaData, LoopConfig loopConfig,
                   Map<Integer, Layer> layers, Instruments instruments, boolean stereo) {

    public float durationSeconds() {
        return durationTicks / ticksPerSecond;
    }
}
