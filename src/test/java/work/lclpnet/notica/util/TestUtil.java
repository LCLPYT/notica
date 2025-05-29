package work.lclpnet.notica.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.lclpnet.kibu.assets.AssetManager;
import work.lclpnet.notica.api.SongDecoder;
import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Instruments;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.NoteSampler;
import work.lclpnet.notica.impl.SoundSampleManager;
import work.lclpnet.notica.impl.UnifiedSoundLoader;
import work.lclpnet.notica.impl.mix.CatmullRomNoteSampler;
import work.lclpnet.notica.impl.mix.SoundMixer;

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

        return createSoundMixer(song, bufferBytes, sampleManager, CatmullRomNoteSampler::new, true);
    }

    public static @NotNull SoundMixer createSoundMixer(Song song, int bufferBytes, SoundSampleManager sampleManager, NoteSamplerFactory factory) throws IOException {
        return createSoundMixer(song, bufferBytes, sampleManager, factory, true);
    }

    public static @NotNull SoundMixer createSoundMixer(Song song, int bufferBytes, SoundSampleManager sampleManager, NoteSamplerFactory factory, boolean directScope) throws IOException {
        initSoundRegistry();

        var noteSampler = factory.create(sampleManager, AUDIO_FORMAT, StereoMode.SPATIAL, song.instruments());

        return new SoundMixer(AUDIO_FORMAT, noteSampler, bufferBytes, directScope);
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

    public static int getBufferByteSize(float seconds) {
        return (int) (AUDIO_FORMAT.getSampleRate() * seconds * AUDIO_FORMAT.getChannels() * (AUDIO_FORMAT.getSampleSizeInBits() / 8.f));
    }

    public static int getFrames(int bufferSize) {
        return (int) (bufferSize / (AUDIO_FORMAT.getChannels() * (AUDIO_FORMAT.getSampleSizeInBits() / 8.f)));
    }

    public interface NoteSamplerFactory {
        NoteSampler create(SoundSampleManager sampleManager, AudioFormat format, StereoMode stereoMode, Instruments instruments);
    }
}
