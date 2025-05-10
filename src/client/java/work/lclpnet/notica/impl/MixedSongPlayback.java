package work.lclpnet.notica.impl;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SoundMixer;
import work.lclpnet.notica.util.ByteBufferInputStream;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static work.lclpnet.notica.impl.mix.SoundMixer.SECTION_LENGTH_MS;

public class MixedSongPlayback {

    private final Song song;
    private final SoundMixer mixer;
    private final Channel channel;
    private final TimeNoiseSampler timeNoise;
    private final int[] sections;
    private final IntSet processed = new IntOpenHashSet();
    private float songVolume = 1f;
    private boolean running = false;
    private int tick = 0;

    public MixedSongPlayback(Song song, SoundMixer mixer, Channel channel, TimeNoiseSampler timeNoise) {
        this.song = song;
        this.mixer = mixer;
        this.channel = channel;
        this.timeNoise = timeNoise;

        this.sections = computeSectionStarts();
    }

    private int[] computeSectionStarts() {
        final int totalTicks = song.durationTicks();
        List<Integer> sectionStarts = new ArrayList<>();
        int offsetTicks = 0;

        while (offsetTicks < totalTicks) {
            sectionStarts.add(offsetTicks);

            int ticks = song.tempo().durationTicks(offsetTicks, SECTION_LENGTH_MS / 1000f);

            offsetTicks += ticks;
        }

        return sectionStarts.stream().mapToInt(i -> i).toArray();
    }

    private int sectionAt(int tick) {
        int i = Arrays.binarySearch(sections, tick);

        if (i >= 0) {
            return i;
        }

        // exact time is not in the sectionStarts array, use the previous section (similar to floor())
        return max(0, -(i + 1) - 1);
    }

    public void start(int startTick, float volume) {
        this.tick = startTick;
        this.songVolume = volume;

        mixer.preloadSounds().thenRun(() -> {
            processSection(0, 0);
            ByteBuffer soundBuf = mixer.completeCurrentBuffer();
            playSound(soundBuf);

            int code = this.hashCode();

//            Thread.ofPlatform().name("NBS playback @ " + code).start(this::runPlayback);
        });
    }

    public void stop() {
        running = false;
    }

    public void seekTo(int tick) {
        this.tick = tick;
        mixer.reset();
    }

    private void runPlayback() {
        final boolean shouldLoop = song.loopConfig().enabled();
        final boolean loopInfinite = song.loopConfig().infinite();
        int loopAmount = song.loopConfig().loopCount();

        running = true;

        while (running) {
            final int section = sectionAt(tick);
            final int nextSection;

            if (section >= sections.length - 1) {
                // this is the last section, check if we need to loop
                if (shouldLoop && (loopInfinite || loopAmount > 0)) {
                    if (!loopInfinite) {
                        loopAmount--;
                    }

                    nextSection = (section + 1) % sections.length;
                } else {
                    // no loop, end after this section
                    nextSection = -1;
                }
            } else {
                // this is not the last section
                nextSection = section + 1;
            }

            // process the current section if needed
            processSection(section, 0);

            // process the next section if needed. required for smooth compressor transition between sections
            if (nextSection >= 0) {
                processSection(nextSection, 1);
            }

            // play the current section
            ByteBuffer soundBuf = mixer.completeCurrentBuffer();
            playSound(soundBuf);

            // wait until next section
            try {
                //noinspection BusyWait
                Thread.sleep(SECTION_LENGTH_MS);
            } catch (InterruptedException e) {
                break;
            } finally {
                freeSection(section);
            }
        }
    }

    private void freeSection(int section) {
        processed.remove(section);
        mixer.advanceBuffer();

        // TODO free openAL source
    }

    private void processSection(int section, int bufferOffset) {
        if (!processed.add(section)) return;

        final int startTick = sections[section];
        final int endTick = section < sections.length - 1 ? sections[section + 1] : song.durationTicks();

        final float sampleRate = mixer.getFormat().getSampleRate();

        int sampleOffset = 0;

        for (int tick = startTick; tick < endTick; tick++) {
            // mix all sounds in current tick
            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(tick);

                if (note == null) continue;

                float volume = songVolume * layer.volume() * 1e-2f;

                if (volume <= 0f) continue;

                short panning = layer.panning();

                if (!mixer.putSound(note, volume, panning, bufferOffset, sampleOffset)) {
                    // TODO schedule long sound playback manually
                }
            }

            // adjust sampleOffset by tick duration
            float tickSeconds = 1.f / song.tempo().tempoAt(tick);
            int tickSamples = (int) ceil(tickSeconds * sampleRate);

            // apply noise to simulate timing imperfections of default SongPlayback
            int noiseSamples = timeNoise.sampleNoiseTime();

            sampleOffset += tickSamples + noiseSamples;
        }
    }

    private void playSound(ByteBuffer buf) {
        Channel.SourceManager sourceManager = channel.createSource(SoundEngine.RunMode.STATIC).join();
        AudioFormat format = mixer.getFormat();
        StaticSound sound = new StaticSound(buf, format);

        sourceManager.run(source -> {
            source.setRelative(true);
            source.setPosition(Vec3d.ZERO);
            source.setBuffer(sound);
            source.play();
        });

        // TODO close sound

        // TODO remove test code
        try (var out = Files.newOutputStream(Path.of("test.wav"))) {
            AudioInputStream in = new AudioInputStream(new ByteBufferInputStream(buf), format, buf.limit());
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, out);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface TimeNoiseSampler {
        TimeNoiseSampler NONE = () -> 0;

        int sampleNoiseTime();
    }
}
