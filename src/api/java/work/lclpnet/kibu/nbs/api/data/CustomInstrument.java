package work.lclpnet.kibu.nbs.api.data;

public interface CustomInstrument {

    /**
     * @return The name of the custom instrument.
     */
    String name();

    /**
     * @return The sound file.
     */
    String soundFile();

    /**
     * @return The key of the instrument, ranges [0, 87], default is 45 (F#4).
     */
    byte key();
}
