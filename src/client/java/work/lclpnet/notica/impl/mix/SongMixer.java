package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

import static java.lang.Math.ceil;

public class SongMixer {

    private final SoundMixer mixer;
    private final Song song;

    private float songVolume = 1f;

    public SongMixer(SoundMixer mixer, Song song) {
        this.mixer = mixer;
        this.song = song;
    }

    public void setSongVolume(float songVolume) {
        this.songVolume = songVolume;
    }

    public void mixTicks(int startTick, int endTick, int bufferOffset) {
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

            sampleOffset += tickSamples;
        }
    }
}
