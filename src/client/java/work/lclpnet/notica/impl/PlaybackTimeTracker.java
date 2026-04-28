package work.lclpnet.notica.impl;

import com.mojang.blaze3d.audio.Channel;
import lombok.Getter;
import work.lclpnet.notica.type.NoticaChannel;

public class PlaybackTimeTracker {

    private final float bufferSeconds;

    private int totalCompletedBuffers = 0;
    @Getter
    private float playbackSeconds = 0;

    public PlaybackTimeTracker(float bufferSeconds) {
        this.bufferSeconds = bufferSeconds;
    }

    public void tick(Channel src) {
        NoticaChannel noticaSrc = (NoticaChannel) src;

        int completedBuffers = noticaSrc.notica$getCompletedBuffers();
        float offsetSeconds = noticaSrc.notica$getOffsetSeconds();

        totalCompletedBuffers += completedBuffers;
        playbackSeconds = totalCompletedBuffers * bufferSeconds + offsetSeconds;
    }
}
