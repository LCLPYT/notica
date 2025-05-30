package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import work.lclpnet.notica.util.TestUtil;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.mockito.Mockito.mock;

class SoundMixerTest {

    private static final boolean EXPORT = false, OPEN = false;

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

        ByteBuffer buf = mixer.applyClamping(totalFrames, mixer.getScope());

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buf);

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