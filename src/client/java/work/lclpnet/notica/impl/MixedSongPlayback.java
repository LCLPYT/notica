package work.lclpnet.notica.impl;

import net.minecraft.client.sound.Channel;
import net.minecraft.client.sound.SoundEngine;
import net.minecraft.client.sound.StaticSound;
import net.minecraft.util.math.Vec3d;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

import static java.lang.Math.ceil;
import static java.lang.Math.min;
import static work.lclpnet.notica.impl.SoundMixer.SECTION_LENGTH_MS;

public class MixedSongPlayback {


    private final Song song;
    private final SoundMixer mixer;
    private final Channel channel;
    private int playbackTick = 0;
    private int processorTick = 0;
    private int currentBuffer = 0;
    private float songVolume = 1f;

    public MixedSongPlayback(Song song, SoundMixer mixer, Channel channel) {
        this.song = song;
        this.mixer = mixer;
        this.channel = channel;
    }

    public void start(int startTick, float volume) {
        this.playbackTick = startTick;
        this.processorTick = startTick;

        this.songVolume = volume;

        mixer.preloadSounds().thenRun(() -> {
            processSection();
            runPlayback();

//            int code = this.hashCode();
//
//            Thread.ofVirtual().name("NBS preprocessor @ " + code).start(this::processSection);
//            Thread.ofPlatform().name("NBS playback @ " + code).start(this::runPlayback);
        });
    }

    private void processSection() {
        float sampleRate = mixer.getFormat().getSampleRate();

        int durationTicks = song.durationTicks();
        int sectionTicks = song.tempo().durationTicks(processorTick, SECTION_LENGTH_MS * 1e-3f);
        int sectionEndTick = processorTick + sectionTicks;
        int ticksToProcess = min(durationTicks, sectionEndTick);

        int sampleOffset = 0;

        for (; processorTick < ticksToProcess; processorTick++) {
            // mix all sounds in current tick
            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(processorTick);

                if (note == null) continue;

                float volume = songVolume * layer.volume() * 1e-2f;

                if (volume <= 0f) continue;

                short panning = layer.panning();

                if (!mixer.putSound(note, volume, panning, currentBuffer, sampleOffset)) {
                    // TODO schedule long sound playback manually
                }
            }

            // adjust sampleOffset by tick duration
            float tickSeconds = 1 / song.tempo().tempoAt(ticksToProcess);
            int tickSamples = (int) ceil(tickSeconds * sampleRate);

            sampleOffset += tickSamples;
        }
    }

    private void runPlayback() {
        Channel.SourceManager sourceManager = channel.createSource(SoundEngine.RunMode.STATIC).join();
        AudioFormat format = mixer.getFormat();
        ByteBuffer buffer = mixer.getBuffer(currentBuffer);
        StaticSound sound = new StaticSound(buffer, format);

        sourceManager.run(source -> {
            source.setRelative(true);
            source.setPosition(Vec3d.ZERO);
            source.setBuffer(sound);
            source.play();
        });

        // TODO close sound
    }
}
