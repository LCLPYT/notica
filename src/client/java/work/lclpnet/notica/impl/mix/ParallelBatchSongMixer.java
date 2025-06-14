package work.lclpnet.notica.impl.mix;

import org.jetbrains.annotations.NotNull;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;

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

    private float songVolume = 1f;

    public ParallelBatchSongMixer(SoundMixer soundMixer, Song song, int workerCount) {
        this.soundMixer = soundMixer;
        this.song = song;
        this.workerCount = workerCount;
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
        final Map<BatchNote, List<BatchNote>> batches = new HashMap<>();

        frameOffset = batchNotes(startTick, endTick, frameOffset, batches);

        dispatchParallel(batches);

        return frameOffset;
    }

    private int batchNotes(int startTick, int endTick, int frameOffset, Map<BatchNote, List<BatchNote>> batches) {
        final float sampleRate = soundMixer.getFormat().getSampleRate();

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

        return frameOffset;
    }

    private void dispatchParallel(Map<BatchNote, List<BatchNote>> batches) {
        var jobs = new ArrayList<>(batches.entrySet());

        // todo sort jobs so that every worker has approximately the same work load

        final int jobCount = batches.size();
        final int assignedWorkers = min(workerCount, jobCount);
        final int batchJobs = (int) ceil(jobCount / (float) assignedWorkers);

        final Thread[] workers = new Thread[assignedWorkers];

        for (int i = 0; i < assignedWorkers; i++) {
            final int jobStart = i * batchJobs;
            final int jobEnd = min(jobStart + batchJobs, jobCount);

            Thread worker = createWorker(jobStart, jobEnd, soundMixer.getWorkerScope(i), jobs);

            workers[i] = worker;
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                break;
            }
        }

        soundMixer.combineScopes(assignedWorkers);
    }

    private @NotNull Thread createWorker(int jobStart, int jobEnd, Scope scope,
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
