package work.lclpnet.kibu.nbs.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Note;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SongSlicerTest {

    @Test
    void getByteSize_testSong_matches() {
        SongSlice slice = new ConcreteSongSlice(ConcreteSongSliceTest.createSong(), 0, 25, -1, 2);
        assertSliceByteSizeMatches(slice);
    }

    private static void assertSliceByteSizeMatches(SongSlice slice) {
        PacketByteBuf buf = PacketByteBufs.create();
        long realSize;

        try {
            SongSlicer.writeSlice(buf, slice);
            realSize = buf.writerIndex();
        } catch (Throwable t) {
            realSize = -1;
        } finally {
            buf.release();
        }

        long size = SongSlicer.getByteSize(slice);
        assertEquals(realSize, size);
    }

    @Test
    void readSlice_writeSlice_working() {
        SongSlice slice = new ConcreteSongSlice(ConcreteSongSliceTest.createSong(), 0, 25, 0, 2);

        PacketByteBuf buf = PacketByteBufs.create();
        SongSlice read;

        try {
            SongSlicer.writeSlice(buf, slice);
            read = SongSlicer.readSlice(buf);
        } catch (Throwable t) {
            read = null;
        } finally {
            buf.release();
        }

        assertNotNull(read);

        assertEquals(slice.tickStart(), read.tickStart());
        assertEquals(slice.tickEnd(), read.tickEnd());
        assertEquals(slice.layerStart(), read.layerStart());
        assertEquals(slice.layerEnd(), read.layerEnd());

        record Event(int tick, int layer, Note note) {}

        var expectedEvents = slice.stream().sequential()
                .map(event -> new Event(event.tick(), event.layer(), event.note()))
                .toList();

        var actualEvents = read.stream().sequential()
                .map(event -> new Event(event.tick(), event.layer(), event.note()))
                .toList();

        assertEquals(expectedEvents, actualEvents);
    }
}