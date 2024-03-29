package work.lclpnet.notica.network;

import net.minecraft.network.PacketByteBuf;
import work.lclpnet.notica.api.NoteEvent;
import work.lclpnet.notica.api.SongSlice;
import work.lclpnet.notica.api.data.Layer;
import work.lclpnet.notica.api.data.Note;
import work.lclpnet.notica.api.data.Song;
import work.lclpnet.notica.impl.FixedIndex;
import work.lclpnet.notica.impl.MutableIndex;
import work.lclpnet.notica.impl.MutableLayer;
import work.lclpnet.notica.impl.data.ImmutableNote;

import java.util.Map;

public class SongSlicer {

    public static SongSlice sliceSeconds(Song song, int tickOffset, int seconds) {
        int tickEnd = Math.min(song.durationTicks(), tickOffset + (int) Math.ceil(seconds * song.ticksPerSecond()));
        int maxLayerIndex = song.layers().streamKeysOrdered().max().orElse(-1);

        return new ConcreteSongSlice(song, tickOffset, tickEnd, 0, maxLayerIndex);
    }

    public static SongSlice sliceAt(Song song, int tickOffset, int layerOffset, long maxBytes) {
        if (maxBytes < 18) throw new IllegalArgumentException("A SongSlice is at least 18 bytes big");

        maxBytes -= 4;  // possible bytes at the end, subtract them from the budget

        int maxLayerIndex = song.layers().streamKeysOrdered().max().orElse(-1);
        SongSlice remaining = new ConcreteSongSlice(song, tickOffset, song.durationTicks(), layerOffset, maxLayerIndex);

        long totalBytes = 16;

        int lastLayer = layerOffset - 1;
        int lastTick = tickOffset - 1;
        boolean firstTick = true;

        for (NoteEvent noteEvent : remaining) {
            int tick = noteEvent.tick();

            if (tick != lastTick) {
                if (firstTick) {
                    firstTick = false;
                } else {
                    // write layer jump end
                    totalBytes += 2;
                }

                // write tick jump
                totalBytes += 2;
            }

            totalBytes += 8;

            if (totalBytes > maxBytes) {
                break;
            }

            lastTick = tick;
            lastLayer = noteEvent.layer();
        }

        if (firstTick) {
            // empty slice
            return new ConcreteSongSlice(new FixedIndex<>(Map.of()), tickOffset, tickOffset - 1, layerOffset, layerOffset - 1);
        }

        return new ConcreteSongSlice(song, tickOffset, lastTick, layerOffset, lastLayer);
    }

    public static long getByteSize(SongSlice slice) {
        int tickStart = slice.tickStart();

        long totalBytes = 16;

        // begin note encoding; similar to nbs format
        int lastTick = tickStart - 1;
        boolean firstTick = true;

        for (NoteEvent noteEvent : slice) {
            int tick = noteEvent.tick();

            if (tick != lastTick) {
                if (firstTick) {
                    firstTick = false;
                } else {
                    // write layer jump end
                    totalBytes += 2;
                }

                // write tick jump
                totalBytes += 2;
                lastTick = tick;
            }

            totalBytes += 8;
        }

        if (!firstTick) {
            // write final layer jump end
            totalBytes += 2;
        }

        // write tick jump end
        totalBytes += 2;

        return totalBytes;
    }

    public static void writeSlice(PacketByteBuf buf, SongSlice slice) {
        int tickStart = slice.tickStart();
        int tickEnd = slice.tickEnd();
        int layerStart = slice.layerStart();
        int layerEnd = slice.layerEnd();

        buf.writeInt(tickStart);
        buf.writeInt(tickEnd);
        buf.writeInt(layerStart);
        buf.writeInt(layerEnd);

        // begin note encoding; similar to nbs format
        int lastTick = tickStart - 1;
        int lastLayer = -1;
        boolean firstTick = true;

        for (NoteEvent noteEvent : slice) {
            int tick = noteEvent.tick();
            int layer = noteEvent.layer();

            if (tick != lastTick) {
                if (firstTick) {
                    firstTick = false;
                } else {
                    // write layer jump end
                    buf.writeShort(0);
                    lastLayer = -1;
                }

                // write tick jump
                buf.writeShort(tick - lastTick);
                lastTick = tick;
            }

            // write layer jump
            buf.writeShort(layer - lastLayer);
            lastLayer = layer;

            // write note
            Note note = noteEvent.note();
            buf.writeByte(note.instrument());
            buf.writeByte(note.key());
            buf.writeByte(note.velocity());
            buf.writeByte(note.panning());  // unsigned
            buf.writeShort(note.pitch());
        }

        if (!firstTick) {
            // write final layer jump end
            buf.writeShort(0);
        }

        // write tick jump end
        buf.writeShort(0);
    }

    public static SongSlice readSlice(PacketByteBuf buf) {
        int tickStart = buf.readInt();
        int tickEnd = buf.readInt();
        int layerStart = buf.readInt();
        int layerEnd = buf.readInt();

        MutableIndex<MutableLayer> layers = new MutableIndex<>();
        int tick = -1;

        while (true) {
            int jump = buf.readShort();
            if (jump == 0) break;

            tick += jump;

            int layer = -1;

            while (true) {
                jump = buf.readShort();
                if (jump == 0) break;

                layer += jump;

                byte instrument = buf.readByte();
                byte key = buf.readByte();
                byte velocity = buf.readByte();
                short panning = buf.readUnsignedByte();
                short pitch = buf.readShort();

                var notes = layers.computeIfAbsent(layer, l -> new MutableLayer());

                Note note = new ImmutableNote(instrument, key, velocity, panning, pitch);
                notes.accept(tick + tickStart, note);
            }
        }

        return new ConcreteSongSlice(layers, tickStart, tickEnd, layerStart, layerEnd);
    }

    public static boolean isFinished(Song song, SongSlice slice) {
        return isFinished(song, slice.tickEnd(), slice.layerEnd());
    }

    public static boolean isFinished(Song song, int tickOffset, int layerOffset) {
        int ticks = song.durationTicks();

        if (tickOffset > ticks) return true;

        if (tickOffset < ticks) return false;

        var layers = song.layers();

        int lastLayer = layers.streamKeysOrdered()
                .filter(i -> {
                    Layer layer = layers.get(i);

                    if (layer == null) return false;

                    return layer.notes().get(ticks) != null;
                })
                .max()
                .orElse(-1);

        return layerOffset >= lastLayer;
    }
}
