package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

import static java.lang.Math.ceil;

public class SimpleSongMixer implements SongMixer {

    private final SoundMixer mixer;
    private final Song song;

    private float songVolume = 1f;

    public SimpleSongMixer(SoundMixer mixer, Song song) {
        this.mixer = mixer;
        this.song = song;
    }

    @Override
    public void setSongVolume(float songVolume) {
        this.songVolume = songVolume;
    }

    /**
     * Mixes all notes in a given tick range into the current sound buffer.
     * @param startTick The start tick (inclusive)
     * @param endTick The end tick (exclusive)
     * @param frameOffset The initial number of frames to skip when mixing.
     * @return The amount of frames that should carry over to the next call of this method.
     */
    @Override
    public int mixTicks(int startTick, int endTick, int frameOffset) {
        final float sampleRate = mixer.getFormat().getSampleRate();

        for (int tick = startTick; tick < endTick; tick++) {
            // mix all sounds in current tick
            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(tick);

                if (note == null) continue;

                float volume = songVolume * layer.volume() * 1e-2f;

                if (volume <= 0f) continue;

                short panning = layer.panning();

                if (!mixer.putSound(note, volume, panning, frameOffset, mixer.getRootScope())) {
                    // TODO schedule long sound playback manually
                }
            }

            // adjust sampleOffset by tick duration
            float tickSeconds = 1.f / song.tempo().tempoAt(tick);
            int tickSamples = (int) ceil(tickSeconds * sampleRate);

            frameOffset += tickSamples;
        }

        return frameOffset;
    }
}
