package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.SongMixer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.ceil;
import static java.lang.Math.min;

public class ParallelBatchSongMixer implements SongMixer {

    private final SoundMixer soundMixer;
    private final Song song;
    private final int workerCount;
    private final SoundMixer.Scope[] scopes;

    private float songVolume = 1f;

    public ParallelBatchSongMixer(SoundMixer soundMixer, Song song, int workerCount) {
        this.soundMixer = soundMixer;
        this.song = song;
        this.workerCount = workerCount;
        this.scopes = new SoundMixer.Scope[workerCount];

        for (int i = 0; i < workerCount; i++) {
            scopes[i] = soundMixer.createScope();
        }
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

        dispatchParallel(batches);

        return frameOffset;
    }

    private void dispatchParallel(Map<BatchNote, List<BatchNote>> batches) {
        var jobs = new ArrayList<>(batches.entrySet());

        final int jobCount = batches.size();
        final int batchJobs = jobCount / workerCount;

        final Thread[] workers = new Thread[workerCount];

        for (int i = 0; i < workerCount; i++) {
            final int jobStart = i * batchJobs;
            final int jobEnd = min(jobStart + batchJobs, jobCount);

            Thread worker = createWorker(jobStart, jobEnd, scopes[i], jobs);

            workers[i] = worker;
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                break;
            }
        }

        // combine scopes into root scope
        var rootScope = soundMixer.getScope();

        rootScope.copy(scopes[0]);

        for (int i = 1; i < workerCount; i++) {
            rootScope.add(scopes[i]);
        }
    }

    private @NotNull Thread createWorker(int jobStart, int jobEnd, SoundMixer.Scope scope,
                                         ArrayList<Map.Entry<BatchNote, List<BatchNote>>> jobs) {

        return Thread.startVirtualThread(() -> {

            for (int j = jobStart; j < jobEnd; j++) {
                var batch = jobs.get(j);

                BatchNote batchNote = batch.getKey();
                Note note = batchNote.note;

                int frameCount = soundMixer.bindSample(note, batchNote.volume, batchNote.panning, scope);

                if (frameCount < 0) continue;

                for (BatchNote inst : batch.getValue()) {
                    if (!soundMixer.mixSample(inst.frameOffset, frameCount, scope)) {
                        // TODO schedule long sound playback manually
                    }
                }
            }
        });
    }

    private record BatchNote(Note note, int frameOffset, float volume, short panning) {
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
}
