package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.GooTypes;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GooContents: multi-type add/remove/merge,
 * capped add, empty detection, totalVolume, typeCount, largestType.
 */
class GooContentsTest {

    // -- empty / basic --

    @Test
    void emptyContentsIsEmpty() {
        assertTrue(GooContents.EMPTY.isEmpty());
        assertEquals(0, GooContents.EMPTY.totalVolume());
        assertEquals(0, GooContents.EMPTY.typeCount());
    }

    @Test
    void singleTypeNotEmpty() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        assertFalse(gc.isEmpty());
        assertEquals(100, gc.totalVolume());
        assertEquals(1, gc.typeCount());
    }

    @Test
    void multiTypeVolumeSumsAll() {
        GooContents gc = new GooContents(Map.of(
                GooTypes.ROCK, 100, GooTypes.METAL, 200, GooTypes.VITAL, 50));
        assertEquals(350, gc.totalVolume());
        assertEquals(3, gc.typeCount());
    }

    // -- isSingleType / getSingleType --

    @Test
    void isSingleType_true() {
        GooContents gc = new GooContents(Map.of(GooTypes.BLAZE, 500));
        assertTrue(gc.isSingleType());
        assertEquals(GooTypes.BLAZE, gc.getSingleType());
    }

    @Test
    void isSingleType_falseForMulti() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100, GooTypes.FROST, 200));
        assertFalse(gc.isSingleType());
    }

    @Test
    void isSingleType_falseForEmpty() {
        assertFalse(GooContents.EMPTY.isSingleType());
    }

    // -- getVolume --

    @Test
    void getVolume_presentType() {
        GooContents gc = new GooContents(Map.of(GooTypes.HEX, 777));
        assertEquals(777, gc.getVolume(GooTypes.HEX));
    }

    @Test
    void getVolume_absentType() {
        GooContents gc = new GooContents(Map.of(GooTypes.HEX, 777));
        assertEquals(0, gc.getVolume(GooTypes.ROCK));
    }

    // -- largestType --

    @Test
    void largestType_empty() {
        assertNull(GooContents.EMPTY.largestType());
    }

    @Test
    void largestType_multiType() {
        GooContents gc = new GooContents(Map.of(
                GooTypes.ROCK, 100, GooTypes.METAL, 500, GooTypes.VITAL, 200));
        assertEquals(GooTypes.METAL, gc.largestType());
    }

    // -- withAdded --

    @Test
    void withAdded_newType() {
        GooContents gc = GooContents.EMPTY.withAdded(GooTypes.LEAF, 100);
        assertEquals(100, gc.getVolume(GooTypes.LEAF));
    }

    @Test
    void withAdded_existingType() {
        GooContents gc = new GooContents(Map.of(GooTypes.LEAF, 100));
        GooContents result = gc.withAdded(GooTypes.LEAF, 50);
        assertEquals(150, result.getVolume(GooTypes.LEAF));
    }

    // -- withRemoved --

    @Test
    void withRemoved_partial() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 500));
        GooContents result = gc.withRemoved(GooTypes.ROCK, 200);
        assertEquals(300, result.getVolume(GooTypes.ROCK));
    }

    @Test
    void withRemoved_full() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 500));
        GooContents result = gc.withRemoved(GooTypes.ROCK, 500);
        assertTrue(result.isEmpty());
    }

    @Test
    void withRemoved_clampsToZero() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents result = gc.withRemoved(GooTypes.ROCK, 999);
        assertEquals(0, result.getVolume(GooTypes.ROCK));
        assertTrue(result.isEmpty());
    }

    // -- mergeWith --

    @Test
    void mergeWith_combinesTypes() {
        GooContents a = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents b = new GooContents(Map.of(GooTypes.METAL, 200));
        GooContents result = a.mergeWith(b);
        assertEquals(100, result.getVolume(GooTypes.ROCK));
        assertEquals(200, result.getVolume(GooTypes.METAL));
    }

    @Test
    void mergeWith_sumsOverlapping() {
        GooContents a = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents b = new GooContents(Map.of(GooTypes.ROCK, 200));
        GooContents result = a.mergeWith(b);
        assertEquals(300, result.getVolume(GooTypes.ROCK));
    }

    // -- withCappedAdd --

    @Test
    void withCappedAdd_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents result = gc.withCappedAdd(GooTypes.METAL, 200, 1000);
        assertEquals(100, result.getVolume(GooTypes.ROCK));
        assertEquals(200, result.getVolume(GooTypes.METAL));
    }

    @Test
    void withCappedAdd_atCapacity() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 900));
        GooContents result = gc.withCappedAdd(GooTypes.METAL, 500, 1000);
        assertEquals(900, result.getVolume(GooTypes.ROCK));
        assertEquals(100, result.getVolume(GooTypes.METAL));
    }

    @Test
    void withCappedAdd_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 1000));
        GooContents result = gc.withCappedAdd(GooTypes.METAL, 500, 1000);
        assertEquals(1000, result.getVolume(GooTypes.ROCK));
        assertEquals(0, result.getVolume(GooTypes.METAL));
    }

    // -- cappedAddAmount --

    @Test
    void cappedAddAmount_underCapacity() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        assertEquals(200, gc.cappedAddAmount(200, 1000));
    }

    @Test
    void cappedAddAmount_overCapacity() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 900));
        assertEquals(100, gc.cappedAddAmount(500, 1000));
    }

    @Test
    void cappedAddAmount_alreadyFull() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 1000));
        assertEquals(0, gc.cappedAddAmount(500, 1000));
    }

    // -- zero/negative filtering --

    @Test
    void constructorFiltersZeroValues() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 0));
        assertTrue(gc.isEmpty());
    }
}
