package work.lclpnet.notica.impl;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.mix.SongAudioStream;
import work.lclpnet.notica.impl.mix.SongMixer;
import work.lclpnet.notica.impl.mix.SoundMixer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static java.lang.Math.max;
import static java.util.concurrent.CompletableFuture.runAsync;

public class MixedSongPlayback {

    private final SongAudioStream audioStream;
    private final Song song;
    private final SoundMixer soundMixer;
    private final SongMixer songMixer;
    private final SoundSampleManager sampleManager;
    private final Channel channel;
    private final Logger logger;
    private final int[] sections;
    private final IntSet processed = new IntOpenHashSet();
    private boolean running = false;
    private int tick = 0;

    public MixedSongPlayback(SongAudioStream audioStream, Song song, SoundMixer soundMixer, SongMixer songMixer,
                             SoundSampleManager sampleManager, Channel channel, Logger logger) {
        this.audioStream = audioStream;
        this.song = song;
        this.soundMixer = soundMixer;
        this.songMixer = songMixer;
        this.sampleManager = sampleManager;
        this.channel = channel;
        this.logger = logger;

        this.sections = computeSectionStarts();
    }

    private int[] computeSectionStarts() {
        final int totalTicks = song.durationTicks();
        List<Integer> sectionStarts = new ArrayList<>();
        int offsetTicks = 0;

//        while (offsetTicks < totalTicks) {
//            sectionStarts.add(offsetTicks);
//
//            int ticks = song.tempo().durationTicks(offsetTicks, SECTION_LENGTH_MS / 1000f);
//
//            offsetTicks += ticks;
//        }

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

        audioStream.setTick(startTick);

        // TODO cleanup
        songMixer.setSongVolume(volume);

        runAsync(sampleManager::loadAll).thenRun(() -> {
//            processSection(0, 0);
//            ByteBuffer soundBuf = soundMixer.completeCurrentBuffer();
            playSound();

            int code = this.hashCode();

//            Thread.ofPlatform().name("NBS playback @ " + code).start(this::runPlayback);
        }).exceptionally(err -> {
            logger.error("Failed to start playback", err);
            return null;
        });
    }

    public void stop() {
        running = false;
    }

    public void seekTo(int tick) {
        this.tick = tick;
        soundMixer.reset();
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
//            ByteBuffer soundBuf = soundMixer.completeCurrentBuffer();
//            playSound(soundBuf);

            // wait until next section
//            try {
                //noinspection BusyWait
//                Thread.sleep(SECTION_LENGTH_MS);
//            } catch (InterruptedException e) {
//                break;
//            } finally {
//                freeSection(section);
//            }
        }
    }

    private void freeSection(int section) {
        processed.remove(section);
        soundMixer.advanceBuffer();

        // TODO free openAL source
    }

    private void processSection(int section, int bufferOffset) {
        if (!processed.add(section)) return;

        final int startTick = sections[section];
        final int endTick = section < sections.length - 1 ? sections[section + 1] : song.durationTicks();

        songMixer.mixTicks(startTick, endTick, bufferOffset);
    }

    private void playSound() {
        Channel.SourceManager sourceManager = channel.createSource(SoundEngine.RunMode.STREAMING).join();

        sourceManager.run(source -> {
            source.setRelative(true);
            source.setPosition(Vec3d.ZERO);
            source.setStream(audioStream);
            source.play();
        });
    }
}
