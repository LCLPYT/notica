package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.StereoMode;
import work.lclpnet.notica.api.data.Song;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.Math.ceil;

public class SongExporter {

    private final SoundSampleManager sampleManager;
    private final NoteSamplerFactory noteSamplerFactory;
    private final AudioFormat inputFormat;
    private final int workerCount;
    private final float chunkSeconds;
    private final float maxExportSeconds;

    public SongExporter(SoundSampleManager sampleManager, NoteSamplerFactory noteSamplerFactory,
                        AudioFormat inputFormat, int workerCount) {
        this(sampleManager, noteSamplerFactory, inputFormat, workerCount, 1f, 4 * 60 * 60);
    }

    public SongExporter(SoundSampleManager sampleManager, NoteSamplerFactory noteSamplerFactory,
                        AudioFormat inputFormat, int workerCount, float chunkSeconds, float maxExportSeconds) {
        this.sampleManager = sampleManager;
        this.noteSamplerFactory = noteSamplerFactory;
        this.inputFormat = inputFormat;
        this.workerCount = workerCount;
        this.chunkSeconds = chunkSeconds;
        this.maxExportSeconds = maxExportSeconds;
    }

    public void export(Song song, float volume, StereoMode stereoMode, Path path) throws IOException {
        final int bufferBytes = SongAudioStream.getByteSize(inputFormat, chunkSeconds);

        NoteSampler noteSampler = noteSamplerFactory.create(sampleManager, inputFormat, stereoMode, song.instruments());
        var soundMixer = new SoundMixer(inputFormat, noteSampler, bufferBytes, workerCount);
        var songMixer = new ParallelBatchSongMixer(soundMixer, song, workerCount);

        songMixer.setSongVolume(volume);

        @SuppressWarnings("resource")
        var stream = new SongAudioStream(inputFormat, soundMixer, songMixer, song,
                soundMixer::applyCompressor, bufferBytes, false);

        // write raw samples to a tmp file first, as the total number of samples is unknown
        Path tmpFile = Files.createTempFile("notica_export", null);
        final int maxIterations = (int) ceil(maxExportSeconds / chunkSeconds);
        int samples = 0;

        try (var out = Files.newOutputStream(tmpFile)) {
            byte[] array = new byte[bufferBytes];

            for (int i = 0; i < maxIterations; i++) {
                ByteBuffer buf = stream.read(bufferBytes);

                if (buf == null) break;

                int len = buf.remaining();

                buf.get(array, 0, len);
                out.write(array, 0, len);

                samples += len;
            }
        }

        // now convert to an actual audio file
        try (var in = Files.newInputStream(tmpFile);
             var out = Files.newOutputStream(path)) {

            AudioInputStream audioIn = new AudioInputStream(in, inputFormat, samples);
            AudioSystem.write(audioIn, AudioFileFormat.Type.WAVE, out);
        }
    }
}
