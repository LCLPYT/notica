package work.lclpnet.notica.impl;

import lombok.Getter;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.Source;
import work.lclpnet.notica.type.NoticaSource;

public class PlaybackTimeTracker {

    private final Channel.SourceManager sourceManager;
    private final float bufferSeconds;

    private int totalCompletedBuffers = 0;
    @Getter
    private float playbackSeconds = 0;

    public PlaybackTimeTracker(Channel.SourceManager sourceManager, float bufferSeconds) {
        this.sourceManager = sourceManager;
        this.bufferSeconds = bufferSeconds;
    }

    public void init() {
        sourceManager.run(src -> ((NoticaSource) src).notica$onTick(this::tick));
    }

    private void tick(Source src) {
        NoticaSource noticaSrc = (NoticaSource) src;

        int completedBuffers = noticaSrc.notica$getCompletedBuffers();
        float offsetSeconds = noticaSrc.notica$getOffsetSeconds();

        totalCompletedBuffers += completedBuffers;
        playbackSeconds = totalCompletedBuffers * bufferSeconds + offsetSeconds;
    }
}
