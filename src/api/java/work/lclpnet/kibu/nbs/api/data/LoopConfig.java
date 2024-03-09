package work.lclpnet.kibu.nbs.api.data;

public interface LoopConfig {

    /**
     * @return True, if looping is enabled.
     */
    boolean enabled();

    /**
     * @return The amount of times the song should loop. 0=infinite.
     */
    int loopCount();

    /**
     * @return The tick where the song player should reset to, when looping.
     */
    int loopStartTick();

    /**
     * @return True, if the song should loop an infinite amount of times.
     */
    default boolean infinite() {
        return loopCount() == 0;
    }
}
