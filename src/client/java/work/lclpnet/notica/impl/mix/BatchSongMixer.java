package work.lclpnet.notica.impl.mix;

import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SongMixer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.ceil;

public class BatchSongMixer implements SongMixer {

    private final SoundMixer soundMixer;
    private final Song song;

    private float songVolume = 1f;

    public BatchSongMixer(SoundMixer soundMixer, Song song) {
        this.soundMixer = soundMixer;
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
        // batch same notes with same volume and panning
        record BatchNote(Note note, int frameOffset, float volume, short panning) {
            @Override
            public boolean equals(Object o) {
                if (o == null || getClass() != o.getClass()) return false;
                BatchNote that = (BatchNote) o;
                return volume == that.volume
                        && panning == that.panning
                        && note.equals(that.note);
            }

            @Override
            public int hashCode() {
                int hash = 1;

                hash = 31 * hash + note.hashCode();
                hash = 31 * hash + Float.hashCode(volume);
                hash = 31 * hash + panning;

                return hash;
            }
        }

        final float sampleRate = soundMixer.getFormat().getSampleRate();
        final Map<BatchNote, List<BatchNote>> batches = new HashMap<>();

        for (int tick = startTick; tick < endTick; tick++) {
            for (Layer layer : song.layers()) {
                Note note = layer.notes().get(tick);

                if (note == null) continue;

                float volume = songVolume * layer.volume() * 1e-2f;

                if (volume <= 0f) continue;

                var inst = new BatchNote(note, frameOffset, volume, layer.panning());

                batches.computeIfAbsent(inst, _inst -> new ArrayList<>()).add(inst);
            }

            // adjust sampleOffset by tick duration
            float tickSeconds = 1.f / song.tempo().tempoAt(tick);
            int tickSamples = (int) ceil(tickSeconds * sampleRate);

            frameOffset += tickSamples;
        }

        for (var batch : batches.entrySet()) {
            BatchNote batchNote = batch.getKey();
            Note note = batchNote.note;

            int frameCount = soundMixer.bindSample(note, batchNote.volume, batchNote.panning, soundMixer.getScope());

            if (frameCount < 0) continue;

            for (BatchNote inst : batch.getValue()) {
                if (!soundMixer.mixSample(inst.frameOffset, frameCount, soundMixer.getScope())) {
                    // TODO schedule long sound playback manually
                }
            }
        }

        return frameOffset;
    }
}
