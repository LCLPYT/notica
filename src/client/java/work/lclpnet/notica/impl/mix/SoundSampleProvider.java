package work.lclpnet.notica.impl.mix;

import java.util.Optional;

public interface SoundSampleProvider {

    Optional<SoundRef> getSample(byte instrument);
}
