package work.lclpnet.notica.impl.mix;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public record SoundSample(ByteBuffer sample, AudioFormat format) {}
