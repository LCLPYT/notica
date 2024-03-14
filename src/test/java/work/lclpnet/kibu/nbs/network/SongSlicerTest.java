package work.lclpnet.kibu.nbs.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import work.lclpnet.kibu.nbs.api.SongSlice;
import work.lclpnet.kibu.nbs.api.data.Note;
import work.lclpnet.kibu.nbs.api.data.Song;
import work.lclpnet.kibu.nbs.test.TestSongHelper;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SongSlicerTest {

    @Test
    void getByteSize_testSong_matches() {
        SongSlice slice = new ConcreteSongSlice(TestSongHelper.createSong(), 0, 25, -1, 2);
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
        SongSlice slice = new ConcreteSongSlice(TestSongHelper.createSong(), 0, 25, 0, 2);

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

    @Test
    void sliceAt_lowCapacity_onlyOneNote() {
        // should not have space for more than one note
        var slice = SongSlicer.sliceAt(TestSongHelper.createSong(), 0, 0, 32);

        assertEquals(0, slice.tickStart());
        assertEquals(0, slice.tickEnd());
        assertEquals(0, slice.layerStart());
        assertEquals(1, slice.layerEnd());
    }

    @Test
    void sliceAt_moreCapacity_twoNotes() {
        // should not have space for more than one note
        var slice = SongSlicer.sliceAt(TestSongHelper.createSong(), 0, 0, 38);

        assertEquals(0, slice.tickStart());
        assertEquals(0, slice.tickEnd());
        assertEquals(0, slice.layerStart());
        assertEquals(2, slice.layerEnd());
    }

    @Test
    void sliceAt_infiniteCapacity_all() {
        // should not have space for more than one note
        Song song = TestSongHelper.createSong();
        var slice = SongSlicer.sliceAt(song, 0, 0, Integer.MAX_VALUE);

        assertEquals(0, slice.tickStart());
        assertEquals(song.durationTicks(), slice.tickEnd());
        assertEquals(0, slice.layerStart());
        assertEquals(0, slice.layerEnd());
    }

    @ParameterizedTest
    @MethodSource("sliceArgs")
    void sliceAt_predefined_equal(int tickStart, int tickEnd, int layerStart, int layerEnd) {
        Song song = TestSongHelper.createSong();

        var tmp = new ConcreteSongSlice(song, tickStart, tickEnd, layerStart, layerEnd);
        long size = SongSlicer.getByteSize(tmp);

        var slice = SongSlicer.sliceAt(song, tickStart, layerStart, size);

        assertEquals(tickStart, slice.tickStart());
        assertEquals(tickEnd, slice.tickEnd());
        assertEquals(layerStart, slice.layerStart());
        assertEquals(layerEnd, slice.layerEnd());
    }

    private static Stream<Arguments> sliceArgs() {
        return Stream.of(
                Arguments.of(0, 10, 2, 2),
                Arguments.of(0, 14, 0, 1),
                Arguments.of(14, 25, 1, 0),
                Arguments.of(0, 25, 1, 0),
                Arguments.of(-10, 25, -2, 0)  // start values are kept
        );
    }

    @Test
    void sliceSeconds_one_asExpected() {
        Song song = TestSongHelper.createSong();

        SongSlice slice = SongSlicer.sliceSeconds(song, 1);

        assertEquals(0, slice.tickStart());
        assertEquals(5, slice.tickEnd());
        assertEquals(0, slice.layerStart());
        assertEquals(2, slice.layerEnd());
    }
}