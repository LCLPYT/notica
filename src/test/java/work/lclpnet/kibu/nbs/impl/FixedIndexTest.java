package work.lclpnet.kibu.nbs.impl;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class FixedIndexTest {

    @Test
    void get_empty_none() {
        var index = new FixedIndex<>(Map.of());

        assertNull(index.get(-1));
        assertNull(index.get(0));
        assertNull(index.get(1));
        assertNull(index.get(2));
    }

    @Test
    void get_simple_correct() {
        Map<Integer, String> map = Map.of(0, "foo", 1, "bar");
        var index = new FixedIndex<>(map);

        assertNull(index.get(-1));
        assertEquals("foo", index.get(0));
        assertEquals("bar", index.get(1));
        assertNull(index.get(2));
    }

    @Test
    void get_withGap_correct() {
        Map<Integer, String> map = Map.of(0, "foo", 2, "bar");
        var index = new FixedIndex<>(map);

        assertNull(index.get(-1));
        assertEquals("foo", index.get(0));
        assertNull(index.get(1));
        assertEquals("bar", index.get(2));
        assertNull(index.get(3));
    }

    @Test
    void get_withNegative_correct() {
        Map<Integer, String> map = Map.of(-3, "foo", 8, "bar", 6, "baz");
        var index = new FixedIndex<>(map);

        assertEquals("foo", index.get(-3));
        assertEquals("bar", index.get(8));
        assertEquals("baz", index.get(6));
        assertNull(index.get(-1));
        assertNull(index.get(0));
        assertNull(index.get(1));
        assertNull(index.get(4));
        assertNull(index.get(9));
    }

    @Test
    void index_empty_empty() {
        var index = new FixedIndex<>(Map.of());

        assertEquals(OptionalInt.empty(), index.index("foo"));
    }

    @Test
    void index_simple_present() {
        var index = new FixedIndex<>(Map.of(-3, "foo", 4, "bar"));

        assertEquals(OptionalInt.of(-3), index.index("foo"));
        assertEquals(OptionalInt.of(4), index.index("bar"));
    }

    @Test
    void index_simpleDifferentInputOrder_present() {
        var index = new FixedIndex<>(Map.of(4, "bar", -3, "foo"));

        assertEquals(OptionalInt.of(-3), index.index("foo"));
        assertEquals(OptionalInt.of(4), index.index("bar"));
    }

    @Test
    void index_nonItem_empty() {
        var index = new FixedIndex<>(Map.of(1, "foo"));

        assertEquals(OptionalInt.empty(), index.index("bar"));
    }

    @Test
    void size_empty_zero() {
        var index = new FixedIndex<>(Map.of());
        assertEquals(0, index.size());
    }

    @Test
    void size_simple_correct() {
        var index = new FixedIndex<>(Map.of(3, "foo", 2, "bar", -1, "baz"));
        assertEquals(3, index.size());
    }

    @Test
    void iterate_empty_none() {
        var index = new FixedIndex<>(Map.of());
        var it = index.iterateOrdered().iterator();

        assertFalse(it.hasNext());
    }

    @Test
    void iterate_one_correct() {
        var index = new FixedIndex<>(Map.of(1, "foo"));
        var it = index.iterateOrdered().iterator();

        assertTrue(it.hasNext());
        var pointer = it.next();

        assertEquals(1, pointer.index());
        assertEquals("foo", pointer.value());

        assertFalse(it.hasNext());
    }

    @Test
    void iterate_multiple_correct() {
        var index = new FixedIndex<>(Map.of(-3, "foo", 10, "bar", 6, "baz"));
        var it = index.iterateOrdered().iterator();

        assertTrue(it.hasNext());

        var pointer = it.next();
        assertEquals(-3, pointer.index());
        assertEquals("foo", pointer.value());

        assertTrue(it.hasNext());

        pointer = it.next();
        assertEquals(6, pointer.index());
        assertEquals("baz", pointer.value());

        assertTrue(it.hasNext());

        pointer = it.next();
        assertEquals(10, pointer.index());
        assertEquals("bar", pointer.value());
    }

    @Test
    void streamKeys_empty_empty() {
        var index = new FixedIndex<>(Map.of());
        assertEquals(0, index.streamKeysOrdered().count());
    }

    @Test
    void streamKeys_simple_rightOrder() {
        var index = new FixedIndex<>(Map.of(-3, "test", 5, "foo", 0, "bar"));
        var indices = index.streamKeysOrdered().toArray();

        assertArrayEquals(new int[] {-3, 0, 5}, indices);
    }
}