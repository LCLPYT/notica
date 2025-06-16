package work.lclpnet.notica.impl.mix;

import java.nio.ByteBuffer;

public interface BufferProcessor {

    ByteBuffer process(int frameCount, Scope scope);
}
