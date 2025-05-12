package work.lclpnet.notica.impl;

import java.util.Optional;

public interface SoundSampleProvider {

    Optional<SoundRef> getSample(byte instrument);
}
