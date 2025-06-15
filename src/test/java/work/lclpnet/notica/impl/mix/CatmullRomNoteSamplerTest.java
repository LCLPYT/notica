package work.lclpnet.notica.impl.mix;

import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import work.lclpnet.notica.impl.FabricInstrumentSoundProvider;
import work.lclpnet.notica.util.NoteHelper;
import work.lclpnet.notica.util.TestUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static work.lclpnet.notica.util.TestUtil.AUDIO_FORMAT;

class CatmullRomNoteSamplerTest {

    private static final boolean EXPORT = false, OPEN = false;

    @Test
    void resample() throws IOException {
        TestUtil.initSoundRegistry();

        float[] baseSample = TestUtil.getSoundSample(FabricInstrumentSoundProvider.FLUTE);
        float[] paddedSample = CatmullRomNoteSampler.paddedSample(baseSample);

        byte key           = 10;
        float notePitch    = 0;

        float pitch = NoteHelper.openAlPitch((short) (key * 100 + notePitch));

        int inFrames = baseSample.length / 2;
        int outFrames = (int) (inFrames / pitch);

        float[] out = new float[AUDIO_FORMAT.getChannels() * (outFrames + 1024)];  // output buffer is bigger than needed most of the time

        int resampledFrames = CatmullRomNoteSampler.resample(paddedSample, out, pitch, AUDIO_FORMAT);

        assertEquals(outFrames, resampledFrames);

        ByteBuffer buffer = BufferUtils.createByteBuffer(resampledFrames * AUDIO_FORMAT.getFrameSize());
        UnifiedSoundLoader.toInterleavedBytes(out, resampledFrames, buffer, AUDIO_FORMAT);

        buffer.flip();

        if (!EXPORT) return;

        Path path = TestUtil.exportSound(buffer);

        System.out.println(path);

        if (!OPEN) return;

        TestUtil.openFile(path.getParent());
    }

    @Test
    void changePitch() throws IOException {
        TestUtil.initSoundRegistry();

        float pitch = 1.5f;

        float[] baseSample = TestUtil.getSoundSample(FabricInstrumentSoundProvider.FLUTE);
        float[] transformed = CatmullRomNoteSampler.changePitch(baseSample, pitch, AUDIO_FORMAT);

        assertEquals((int) (baseSample.length / pitch), transformed.length);

        if (!EXPORT) return;

        var path = TestUtil.exportSound(transformed);

        System.out.println(path);

        if (!OPEN) return;

        TestUtil.openFile(path);
    }
}