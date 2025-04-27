package work.lclpnet.notica.util;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static java.lang.Math.min;

public class ByteBufferInputStream extends InputStream {

    private final ByteBuffer buf;
    private int markedPos = -1;

    public ByteBufferInputStream(ByteBuffer buf) {
        this.buf = buf;
    }

    public int read() {
        if (!buf.hasRemaining()) {
            return -1;
        }

        return buf.get() & 0xFF;
    }

    public int read(byte @NotNull [] bytes, int off, int len) {
        if (!buf.hasRemaining()) {
            return -1;
        }

        len = min(len, buf.remaining());
        buf.get(bytes, off, len);

        return len;
    }

    @Override
    public void mark(int readlimit) {
        markedPos = buf.position();
    }

    @Override
    public void reset() {
        buf.position(markedPos);
    }
}
