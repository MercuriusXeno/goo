package com.mercuriusxeno.goo.block;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for crucible pure logic: GooValue-to-GooContents bridge,
 * GooContents operations, and platform movement math.
 */
@ExtendWith(MockitoExtension.class)
class CrucibleBlockEntityTest {

    // -- GooValue.toGooContents ------------------------------------------

    /**
     * Empty GooValue produces EMPTY GooContents.
     */
    @Test
    void emptyGooValueProducesEmptyGooContents() {
        GooContents result = GooValue.EMPTY.toGooContents();
        assertTrue(result.isEmpty());
    }

    /**
     * Single-type GooValue uses int correctly.
     */
    @Test
    void singleTypeGooValueConvertsToGooContents() {
        GooValue value = new GooValue(Map.of(GooTypes.ROCK, 500));
        GooContents result = value.toGooContents();
        assertEquals(500, result.getVolume(GooTypes.ROCK));
        assertEquals(1, result.typeCount());
    }

    /**
     * Multi-type GooValue preserves all types with widened amounts.
     */
    @Test
    void multiTypeGooValuePreservesAllTypes() {
        GooValue value = new GooValue(Map.of(
                GooTypes.ROCK, 100,
                GooTypes.METAL, 250,
                GooTypes.VITAL, 50
        ));
        GooContents result = value.toGooContents();
        assertEquals(100, result.getVolume(GooTypes.ROCK));
        assertEquals(250, result.getVolume(GooTypes.METAL));
        assertEquals(50, result.getVolume(GooTypes.VITAL));
        assertEquals(3, result.typeCount());
    }

    // -- GooContents.largestType -----------------------------------------

    /**
     * Empty GooContents has no largest type.
     */
    @Test
    void emptyGooContentsHasNoLargestType() {
        assertNull(GooContents.EMPTY.largestType());
    }

    /**
     * Single-type GooContents returns that type as largest.
     */
    @Test
    void singleTypeGooContentsReturnsThatType() {
        GooContents gc = new GooContents(Map.of(GooTypes.BLAZE, 100));
        assertEquals(GooTypes.BLAZE, gc.largestType());
    }

    /**
     * Multi-type GooContents returns the highest-volume type.
     */
    @Test
    void multiTypeGooContentsReturnsHighestVolume() {
        GooContents gc = new GooContents(Map.of(
                GooTypes.ROCK, 100,
                GooTypes.METAL, 500,
                GooTypes.VITAL, 200
        ));
        assertEquals(GooTypes.METAL, gc.largestType());
    }

    // -- GooContents.mergeWith -------------------------------------------

    /**
     * Merging empty with non-empty returns the non-empty.
     */
    @Test
    void mergeEmptyWithNonEmptyReturnsNonEmpty() {
        GooContents gc = new GooContents(Map.of(GooTypes.ROCK, 100));
        GooContents result = GooContents.EMPTY.mergeWith(gc);
        assertEquals(100, result.getVolume(GooTypes.ROCK));
    }

    /**
     * Merging two contents sums matching types.
     */
    @Test
    void mergeWithSumsMatchingTypes() {
        GooContents a = new GooContents(Map.of(GooTypes.ROCK, 100, GooTypes.METAL, 50));
        GooContents b = new GooContents(Map.of(GooTypes.ROCK, 200, GooTypes.VITAL, 75));
        GooContents result = a.mergeWith(b);
        assertEquals(300, result.getVolume(GooTypes.ROCK));
        assertEquals(50, result.getVolume(GooTypes.METAL));
        assertEquals(75, result.getVolume(GooTypes.VITAL));
    }
}
