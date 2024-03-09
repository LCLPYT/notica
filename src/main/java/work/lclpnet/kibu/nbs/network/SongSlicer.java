package work.lclpnet.kibu.nbs.network;

import net.minecraft.network.PacketByteBuf;
import work.lclpnet.kibu.nbs.api.Index;
import work.lclpnet.kibu.nbs.api.NoteEvent;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.NoteContainer;
import work.lclpnet.kibu.nbs.impl.ListIndex;
import work.lclpnet.kibu.nbs.impl.data.ImmutableNote;

import java.util.HashMap;
import java.util.Map;

public class SongSlicer {

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
            buf.writeByte(note.panning());
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

        Map<Integer, Map<Integer, Note>> layerNotes = new HashMap<>();
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
                byte panning = buf.readByte();
                short pitch = buf.readShort();

                var notes = layerNotes.computeIfAbsent(layer, l -> new HashMap<>());

                Note note = new ImmutableNote(instrument, key, velocity, panning, pitch);
                notes.put(tick, note);
            }
        }

        Map<Integer, NoteContainer> layerMap = new HashMap<>(layerNotes.size());

        for (var entry : layerNotes.entrySet()) {
            layerMap.put(entry.getKey(), () -> new ListIndex<>(entry.getValue()));
        }

        Index<NoteContainer> layers = new ListIndex<>(layerMap);

        return new ConcreteSongSlice(layers, tickStart, tickEnd, layerStart, layerEnd);
    }
}
