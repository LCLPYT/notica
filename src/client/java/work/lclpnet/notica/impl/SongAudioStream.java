package work.lclpnet.notica.impl;

import net.minecraft.client.sound.AudioStream;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

import static java.lang.Math.min;

public class SongAudioStream implements AudioStream {

//    private static final int BASE_BUFFER_COUNT = 4;
//    private static final float MAX_SOUND_SECONDS = 16.f;

    private final AudioFormat format;
    private final Song song;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
//    private final ByteBuffer[] buffers;

    private boolean first = true;
    private int tick = 0;

    public SongAudioStream(AudioFormat format, SoundMixer soundMixer, SongMixer songMixer, Song song) {
        this.format = format;
        this.soundMixer = soundMixer;
        this.songMixer = songMixer;
        this.song = song;

//        final int byteSize = getByteSize(format, segmentSeconds);
//        final int extraBufCount = (int) ceil(MAX_SOUND_SECONDS / segmentSeconds);
//        final int bufCount = BASE_BUFFER_COUNT + extraBufCount;

//        buffers = new ByteBuffer[bufCount];
//
//        for (int i = 0; i < buffers.length; i++) {
//            buffers[i] = ByteBuffer.allocateDirect(byteSize);
//        }
    }

    public static int getByteSize(AudioFormat format, float seconds) {
        return (int) (seconds * format.getSampleSizeInBits() / 8.0F * format.getChannels() * format.getSampleRate());
    }

    public static int getFrameCount(AudioFormat format, int byteSize) {
        return (int) (byteSize / ((format.getSampleSizeInBits() / 8.0F) * format.getChannels()));
    }

    public static float getSeconds(AudioFormat format, int frameCount) {
        return (int) (frameCount / format.getSampleRate());
    }

    @Override
    public AudioFormat getFormat() {
        return format;
    }

    @Override
    public ByteBuffer read(int size) {
        final int frameCount = getFrameCount(format, size);
        float seconds = getSeconds(format, frameCount);

        if (first) {
            first = false;
            seconds += soundMixer.getCompressorLookAheadSeconds();
        }

        int durationTicks = song.tempo().durationTicks(tick, seconds);
        int endTick = min(tick + durationTicks, song.durationTicks());

        if (endTick <= tick) {
            // song ended
            return null;
        }

        songMixer.mixTicks(tick, endTick, 0);

        // TODO make advanceBuffer internal and call it when the position of the buffer exceeds it's limit
        ByteBuffer buf = soundMixer.completeCurrentBuffer(frameCount);

        soundMixer.advanceBuffer();
        tick = endTick;

        return buf;
    }

    @Override
    public void close() {}

    public void setTick(int tick) {
        this.tick = tick;
    }
}
