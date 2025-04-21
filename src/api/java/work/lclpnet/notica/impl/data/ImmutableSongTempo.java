package work.lclpnet.notica.impl.data;

import org.jetbrains.annotations.VisibleForTesting;
import work.lclpnet.notica.api.data.SongTempo;
import work.lclpnet.notica.api.data.TempoChange;

import java.util.*;

import static java.lang.Math.*;
import static java.util.Comparator.comparingInt;

public class ImmutableSongTempo implements SongTempo {

    private final Set<Integer> changeTimes;
    private final List<TempoChange> sections;
    private final float[] sectionTempo;
    private final int[] sectionStarts;

    public ImmutableSongTempo(List<TempoChange> tempoChanges) {
        changeTimes = new HashSet<>(tempoChanges.size());

        // in case there are multiple changes for the same time ticks, we only care about the last one
        // reverse the list and ignore changes with existing times
        List<TempoChange> filtered = new ArrayList<>(tempoChanges.size());

        tempoChanges = new ArrayList<>(tempoChanges);
        Collections.reverse(tempoChanges);

        for (TempoChange change : tempoChanges) {
            int time = change.timeTick();

            if (time < 0 || !changeTimes.add(time)) continue;

            filtered.add(change);
        }

        // finally sort by time ascending, so we can use binary search to quickly find the section at a given time
        filtered.sort(comparingInt(TempoChange::timeTick));

        // populate section data
        int size = filtered.size();

        sectionStarts = new int[size];
        sectionTempo = new float[size];

        for (int i = 0; i < size; i++) {
            TempoChange change = filtered.get(i);
            sectionStarts[i] = change.timeTick();
            sectionTempo[i] = change.ticksPerSecond();
        }

        this.sections = Collections.unmodifiableList(filtered);
    }

    @Override
    public List<TempoChange> changes() {
        return sections;
    }

    @Override
    public boolean changeAt(int timeTick) {
        return changeTimes.contains(timeTick);
    }

    @Override
    public float tempoAt(int timeTick) {
        return sectionTempo[sectionAt(timeTick)];
    }

    @VisibleForTesting
    int sectionAt(int timeTick) {
        int i = Arrays.binarySearch(sectionStarts, timeTick);

        if (i >= 0) {
            return i;
        }

        // exact time is not in the sectionStarts array, use the previous section (similar to floor())
        return max(0, -(i + 1) - 1);

    }

    @Override
    public float durationSeconds(int _startTicks, int durationTicks) {
        int _endTicks = _startTicks + durationTicks;

        int startTicks = min(_startTicks, _endTicks);
        int endTicks = max(_startTicks, _endTicks);

        int startSection = sectionAt(startTicks);
        int endSection = sectionAt(endTicks);

        float timeSeconds = 0;
        int offsetTicks = startTicks;
        int sectionTicks;

        // start section
        sectionTicks = getSectionTicks(startSection, offsetTicks, durationTicks);
        timeSeconds += sectionTicks / sectionTempo[startSection];
        offsetTicks += sectionTicks;
        durationTicks -= sectionTicks;

        // sections in between
        for (int section = startSection + 1; section < endSection; section++) {
           sectionTicks = getSectionTicks(section, offsetTicks, durationTicks);
           timeSeconds += sectionTicks / sectionTempo[section];
           offsetTicks += sectionTicks;
           durationTicks -= sectionTicks;
        }

        // end section
        if (endSection != startTicks) {
            sectionTicks = getSectionTicks(endSection, offsetTicks, durationTicks);
            timeSeconds += sectionTicks / sectionTempo[endSection];
        }

        return timeSeconds;
    }

    @Override
    public int durationTicks(int offsetTicks, float remainingSeconds) {
        int durationTicks = 0;

        while (remainingSeconds > 0) {
            int section = sectionAt(offsetTicks);

            if (section < sectionStarts.length - 1) {
                // there is a next section, get section length in ticks, then convert to seconds
                int sectionTicks = sectionStarts[section + 1] - offsetTicks;
                float sectionSeconds = sectionTicks / sectionTempo[section];
                float seconds = min(remainingSeconds, sectionSeconds);

                // adjust ticks in case the remaining seconds cap was reached
                if (seconds < sectionSeconds) {
                    sectionTicks = (int) ceil(seconds * sectionTempo[section]);
                }

                durationTicks += sectionTicks;
                remainingSeconds -= seconds;
                offsetTicks += sectionTicks;
            } else {
                // this is the last section, return remaining seconds as ticks
                durationTicks += (int) ceil(remainingSeconds * sectionTempo[0]);
                remainingSeconds = 0;
            }
        }

        return durationTicks;
    }

    private int getSectionTicks(int section, int offsetTicks, int remainingTicks) {
        if (section < sectionStarts.length - 1) {
            // there is a next section, return remaining ticks, capped by ticks until next section
            return min(remainingTicks, sectionStarts[section + 1] - offsetTicks);
        }

        // this is the last section, return remaining ticks
        return remainingTicks;
    }
}
