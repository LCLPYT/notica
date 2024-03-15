package work.lclpnet.notica.api.data;

public interface Note {

    /**
     * @return The instrument index.
     */
    byte instrument();

    /**
     * @return The key of the note, ranging [0, 87], where [33, 57] is the vanilla range.
     */
    byte key();

    /**
     * @return The velocity (volume) of this note, ranging [0, 100].
     */
    byte velocity();

    /**
     * @return The panning of this note, ranging [0, 200], where 100=center.
     */
    short panning();

    /**
     * @return The additional fine-tuning pitch of this note. Typically, within [-1200, 1200], where 100 is one semitone difference.
     */
    short pitch();
}
