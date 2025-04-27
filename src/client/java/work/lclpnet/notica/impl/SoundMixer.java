package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.SoundLoader;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import work.lclpnet.notica.mixin.client.StaticSoundAccessor;
import work.lclpnet.notica.util.ByteBufferInputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Objects.requireNonNull;

public class SoundMixer {

    private final SoundLoader soundLoader;
    private final Channel channel;
    private final Logger logger;
    private final AudioFormat targetFormat;
    private final Map<Identifier, CompletableFuture<ByteBuffer>> unifiedSamples = new HashMap<>();

    public SoundMixer(SoundLoader soundLoader, Channel channel, Logger logger) {
        this.soundLoader = soundLoader;
        this.channel = channel;
        this.logger = logger;
        this.targetFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                48_000, 16, 2, 4, 48_000, false);
    }

    public void playMerged() {
        var pling = getUnifiedSample(Identifier.of("sounds/note/pling.ogg"));
        var ironXylo = getUnifiedSample(Identifier.of("sounds/note/iron_xylophone.ogg"));

        ByteBuffer plingBuf = requireNonNull(pling.join(), "pling is null");
        ByteBuffer ironXyloBuf = requireNonNull(ironXylo.join(), "iron xylo is null");

        plingBuf.position(0);
        ironXyloBuf.position(0);

        ByteBuffer mixed = mix(plingBuf, ironXyloBuf);

        mixed.position(0);

        Channel.SourceManager sourceManager = channel.createSource(SoundEngine.RunMode.STATIC).join();

        sourceManager.run(source -> {
            source.setRelative(true);
            source.setPosition(Vec3d.ZERO);
            source.setBuffer(new StaticSound(mixed, targetFormat));
            source.play();
        });
    }

    private synchronized CompletableFuture<@Nullable ByteBuffer> getUnifiedSample(Identifier soundId) {
        CompletableFuture<ByteBuffer> future = unifiedSamples.get(soundId);

        if (future != null) {
            return future;
        }

        future = soundLoader.loadStatic(soundId).thenApply(sound -> {
            var soundAccessor = (StaticSoundAccessor) sound;

            try {
                return recode(soundAccessor.getSample(), soundAccessor.getFormat());
            } catch (UnsupportedAudioFileException | IOException e) {
                throw new RuntimeException("Failed to recode sound", e);
            }
        }).exceptionally(err -> {
            logger.error("Failed to get unified sample", err);

            synchronized (this) {
                unifiedSamples.remove(soundId);
            }

            return null;
        });

        unifiedSamples.put(soundId, future);

        return future;
    }

    private ByteBuffer recode(ByteBuffer sample, AudioFormat format) throws UnsupportedAudioFileException, IOException {
        if (targetFormat.matches(format)) {
            return sample;
        }

        var src = new AudioInputStream(new ByteBufferInputStream(sample), format, sample.remaining());
        var dst = AudioSystem.getAudioInputStream(targetFormat, src);

        byte[] bytes = dst.readAllBytes();
        ByteBuffer buf = BufferUtils.createByteBuffer(bytes.length);

        buf.put(bytes);

        return buf;
    }

    private ByteBuffer mix(ByteBuffer x, ByteBuffer y) {
        int channels = targetFormat.getChannels();
        int frameSize = targetFormat.getFrameSize();
        int frameX = x.remaining() / frameSize;
        int frameY = y.remaining() / frameSize;

        int frames = max(frameX, frameY);
        var dst = BufferUtils.createByteBuffer(frames * frameSize);

        for (int frame = 0; frame < frames; frame++) {
            for (int channel = 0; channel < channels; channel++) {
                short sx = frame < frameX ? x.getShort() : 0;
                short sy = frame < frameY ? y.getShort() : 0;
                short mixed = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, sx + sy));

                dst.putShort(mixed);
            }
        }

        return dst;
    }
}
