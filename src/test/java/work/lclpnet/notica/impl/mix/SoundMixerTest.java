package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.util.TestUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static java.lang.Math.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

class SoundMixerTest {

    private static final boolean EXPORT = true, OPEN = true;

    @Test
    void mixSample() throws IOException {
        final int bufferFrames = 500;
        final int bufferSamples = bufferFrames * 2;

        var mixer = mockedMixer(bufferSamples * 2);

        final int totalFrames = 1500;
        final int totalSamples = totalFrames * 2;

        float[] sine = new float[totalSamples];

        for (int i = 0; i < totalFrames; i++) {
            float v = (float) Math.sin(Math.toRadians(i));

            sine[i] = v;
            sine[i + totalFrames] = v;
        }

        mixer.mixSample(sine, totalFrames, 0, 0, mixer.getScope());

        ByteBuffer combined = BufferUtils.createByteBuffer(totalSamples * 2);

        int iterations = (int) ceil(totalFrames / (double) bufferFrames);

        for (int i = 0; i < iterations; i++) {
            ByteBuffer buf = mixer.applyClamping(bufferFrames, mixer.getScope());

            combined.put(buf);

            mixer.advanceBuffer();
        }

        combined.flip();

        // left channel
        for (int i = 0; i < totalFrames; i++) {
            float v = sine[i];
            short expected = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, v * Short.MAX_VALUE));

            short actual = combined.getShort(i * 4);

            if (expected == actual) continue;

            fail("Mismatch in left channel at index [%s]: Expected <%s> but was <%s>".formatted(i, expected, actual));
        }

        // right channel
        for (int i = 0; i < totalFrames; i++) {
            float v = sine[i + totalFrames];
            short expected = (short) max(Short.MIN_VALUE, min(Short.MAX_VALUE, v * Short.MAX_VALUE));

            short actual = combined.getShort(i * 4 + 2);

            if (expected == actual) continue;

            fail("Mismatch in left channel at index [%s]: Expected <%s> but was <%s>".formatted(i, expected, actual));
        }

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(combined);

        System.out.println(path.toAbsolutePath());

        if (!OPEN) return;

        TestUtil.openFile(path.getParent());
    }

    @SuppressWarnings("SameParameterValue")
    private @NotNull SoundMixer mockedMixer(int bufferSize) {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 48000, 16, 2, 4, 48000, false);
        return new SoundMixer(format, mock(), bufferSize, 1);
    }
}