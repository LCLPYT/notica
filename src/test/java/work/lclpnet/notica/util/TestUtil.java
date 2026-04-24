package work.lclpnet.notica.util;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.assets.AssetManager;
import work.lclpnet.notica.api.SongDecoder;
import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.data.ImmutableInstruments;
import work.lclpnet.notica.impl.mix.*;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.UnaryOperator;

import static java.lang.Math.abs;

public class TestUtil {

    public static final Logger logger = LoggerFactory.getLogger(TestUtil.class);
    public static final AudioFormat AUDIO_FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            48_000, 16, 2, 4, 48_000, false);
    private static final String ASSETS_VERSION = "1.21.5";

    private static volatile boolean registryInit = false;
    private static final AssetManager assetManager = AssetManager.getShared(ASSETS_VERSION);
    private static final TestSoundRegistry soundRegistry = new TestSoundRegistry(assetManager);
    private static final UnifiedSoundLoader soundLoader = new UnifiedSoundLoader(AUDIO_FORMAT, logger);

    public static @NotNull Song loadSong(String name, Class<?> clazz) throws IOException {
        InputStream in = clazz.getResourceAsStream("/songs/" + name);
        Objects.requireNonNull(in, "Song not found");

        try (in) {
            return SongDecoder.parse(in);
        }
    }

    public static @NotNull Song loadSong(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            return SongDecoder.parse(in);
        }
    }

    public static @NotNull SoundSampleManager createSampleManager(Instruments instruments) {
        return createSampleManager(instruments, UnaryOperator.identity());
    }

    public static @NotNull SoundSampleManager createSampleManager(Instruments instruments, UnaryOperator<float[]> sampleTransformer) {
        var sampleProvider = new TestSoundSampleProvider(instruments, soundRegistry);

        return new SoundSampleManager(instruments, sampleProvider, soundLoader, sampleTransformer);
    }

    public static @NotNull SoundMixer createSoundMixer(Song song, int bufferBytes) throws IOException {
        SoundSampleManager sampleManager = createSampleManager(song.instruments(), CatmullRomNoteSampler::paddedSample);

        initSoundRegistry();
        sampleManager.loadAll();

        return createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new, 1);
    }

    public static @NotNull SoundMixer createSoundMixer(Song song, int bufferBytes, SoundSampleManager sampleManager, NoteSamplerFactory factory) throws IOException {
        return createSoundMixer(song, bufferBytes, sampleManager, factory, 1);
    }

    public static @NotNull SoundMixer createSoundMixer(Song song, int bufferBytes, SoundSampleManager sampleManager, NoteSamplerFactory factory, int workerCount) throws IOException {
        initSoundRegistry();

        var noteSampler = factory.create(sampleManager, AUDIO_FORMAT, StereoMode.SPATIAL, song.instruments());

        return new SoundMixer(AUDIO_FORMAT, noteSampler, bufferBytes, workerCount, 1);
    }

    public static Path exportSound(ByteBuffer buffer) throws IOException {
        Path path = Files.createTempFile("notica_test", ".wav");

        exportSound(buffer, path);

        return path;
    }

    public static void exportSound(ByteBuffer buffer, Path path) throws IOException {
        try (var out = Files.newOutputStream(path)) {
            AudioInputStream in = new AudioInputStream(new ByteBufferInputStream(buffer), AUDIO_FORMAT, buffer.limit());
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, out);
        }
    }

    public static void initSoundRegistry() throws IOException {
        if (registryInit) return;

        synchronized (TestUtil.class) {
            if (registryInit) return;

            registryInit = true;
            soundRegistry.init();
        }
    }

    public static float[] getSoundSample(int instrument) {
        var provider = new TestSoundSampleProvider(ImmutableInstruments.DEFAULT, soundRegistry);

        SoundRef ref = provider.getSample((byte) instrument).orElseThrow();

        return soundLoader.getUnifiedSample(ref).join().orElseThrow();
    }

    public static int getBufferByteSize(float seconds) {
        return (int) (AUDIO_FORMAT.getSampleRate() * seconds * AUDIO_FORMAT.getChannels() * (AUDIO_FORMAT.getSampleSizeInBits() / 8.f));
    }

    public static int getFrames(int bufferSize) {
        return (int) (bufferSize / (AUDIO_FORMAT.getChannels() * (AUDIO_FORMAT.getSampleSizeInBits() / 8.f)));
    }

    public static void openFile(Path path) throws IOException {
        String absPath = path.toAbsolutePath().toString();
        String os = System.getProperty("os.name").toLowerCase();

        String[] args;

        if (os.contains("win")) {
            args = new String[] {"cmd", "/c", "start", "\"\"", "\"" + absPath + "\"" };
        } else if (os.contains("mac")) {
            args = new String[] {"open", absPath};
        } else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            args = new String[]{"xdg-open", absPath};
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: " + os);
        }

        Runtime.getRuntime().exec(args);
    }

    public static byte[] asByteArray(ByteBuffer buf) {
        byte[] array = new byte[buf.limit()];

        for (int j = 0; j < array.length; j++) {
            array[j] = buf.get();
        }

        return array;
    }

    public static short[] asShortArray(ByteBuffer buf) {
        short[] array = new short[buf.limit() / 2];

        for (int j = 0; j < array.length; j++) {
            array[j] = buf.getShort();
        }

        return array;
    }

    public static float[] toFloatArray(short[] samples) {
        float[] array = new float[samples.length];

        for (int i = 0; i < array.length; i++) {
            array[i] = samples[i] / (float) Short.MAX_VALUE;
        }

        return array;
    }

    public static Path exportSound(float[] samples) throws IOException {
        var buf = BufferUtils.createByteBuffer(samples.length * 2);

        UnifiedSoundLoader.toInterleavedBytes(samples, samples.length / 2, buf, AUDIO_FORMAT);

        buf.flip();

        return exportSound(buf);
    }

    public static void assertArrayEquals(float[] expected, float[] actual, float tol, String msg) {
        if (expected.length != actual.length) {
            throw new AssertionError("Array lengths differ: Expected: <%s> but was: <%s>".formatted(expected.length, actual.length));
        }

        float bufferSamples = AUDIO_FORMAT.getSampleRate() * AUDIO_FORMAT.getChannels();

        for (int i = 0; i < expected.length; i++) {
            float e = expected[i];
            float a = actual[i];

            if (abs(e - a) <= tol) continue;

            throw new AssertionError("%s (buffer_size=%s, time=%.2fs, look_ahead=0.01s) ==> array contents differ at index [%s], expected: <%s> (<%s>) but was: <%s> (<%s>)"
                    .formatted(msg, bufferSamples, i / bufferSamples,
                            i, e, (int) (e * Short.MAX_VALUE), a, (int) (a * Short.MAX_VALUE)));
        }
    }
}
