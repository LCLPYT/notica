package work.lclpnet.notica.api.data;

import java.util.List;

public interface SongTempo {

    /**
     * Get the song sections, described by tempo changes.
     * @return The song sections, represented by tempo changes.
     */
    List<TempoChange> changes();

    /**
     * Check whether the tempo should change at a given time tick.
     * @param timeTick The time tick.
     * @return True, if the tempo changes at the given time tick. False if there is no change to the previous one.
     */
    boolean changeAt(int timeTick);

    /**
     * Get the tempo in ticks per second at a given time tick.
     * @param timeTick The time tick.
     * @return The song tempo in ticks per second of the song section at the given time.
     */
    float tempoAt(int timeTick);

    /**
     * Computes the duration in seconds for a given time range in ticks.
     * This respects the tempo at the of the song segments in the specified tick range.
     * @param offsetTicks The absolute time offset in ticks.
     * @param durationTicks The duration to convert in ticks.
     * @return The duration of the time range in seconds, respecting song section tempos.
     */
    float durationSeconds(int offsetTicks, int durationTicks);

    /**
     * Computes the duration in ticks for a given time range in seconds.
     * @param offsetTicks The absolute time offset in ticks.
     * @param durationSeconds The duration to convert in seconds.
     * @return The duration of the time range in ticks, respecting song section tempos.
     */
    int durationTicks(int offsetTicks, float durationSeconds);
}
