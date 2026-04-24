package work.lclpnet.notica.impl.mix;

import net.minecraft.client.sounds.AudioStream;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

public class SongAudioStream implements AudioStream {

    private final Supplier<@Nullable ByteBuffer[]> bufferSupplier;
    private final AudioFormat format;
    private final int outputBufferIndex;
    private final Runnable onClose;
    private boolean closed = false;

    public SongAudioStream(Supplier<@Nullable ByteBuffer[]> bufferSupplier, AudioFormat format, int outputBufferIndex, Runnable onClose) {
        this.bufferSupplier = bufferSupplier;
        this.format = format;
        this.outputBufferIndex = outputBufferIndex;
        this.onClose = onClose;
    }

    @Override
    public @NonNull AudioFormat getFormat() {
        return format;
    }

    @Override
    public @Nullable ByteBuffer read(int size) {
        @Nullable ByteBuffer[] buffers = bufferSupplier.get();

        if (buffers == null) return null;

        return buffers[outputBufferIndex];
    }

    @Override
    public void close() {
        synchronized (this) {
            if (closed) return;

            closed = true;
        }

        onClose.run();
    }
}
