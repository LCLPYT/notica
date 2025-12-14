package work.lclpnet.notica.impl;

import com.mojang.blaze3d.audio.Channel;
import lombok.Getter;
import net.minecraft.client.sounds.ChannelAccess;
import work.lclpnet.notica.type.NoticaSource;

public class PlaybackTimeTracker {

    private final ChannelAccess.ChannelHandle sourceManager;
    private final float bufferSeconds;

    private int totalCompletedBuffers = 0;
    @Getter
    private float playbackSeconds = 0;

    public PlaybackTimeTracker(ChannelAccess.ChannelHandle sourceManager, float bufferSeconds) {
        this.sourceManager = sourceManager;
        this.bufferSeconds = bufferSeconds;
    }

    public void init() {
        sourceManager.execute(src -> ((NoticaSource) src).notica$onTick(this::tick));
    }

    private void tick(Channel src) {
        NoticaSource noticaSrc = (NoticaSource) src;

        int completedBuffers = noticaSrc.notica$getCompletedBuffers();
        float offsetSeconds = noticaSrc.notica$getOffsetSeconds();

        totalCompletedBuffers += completedBuffers;
        playbackSeconds = totalCompletedBuffers * bufferSeconds + offsetSeconds;
    }
}
