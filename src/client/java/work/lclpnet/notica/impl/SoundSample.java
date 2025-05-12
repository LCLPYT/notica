package work.lclpnet.notica.impl;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public record SoundSample(ByteBuffer sample, AudioFormat format) {}
