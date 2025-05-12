package work.lclpnet.notica.impl;

import java.util.concurrent.CompletableFuture;

public interface SoundRef {

    float volume();

    float pitch();

    CompletableFuture<SoundSample> load();
}
