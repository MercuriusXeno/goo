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
 * Tests for the crucible's GooValue-to-GooContents bridge, the melt payout.
 * GooContentsTest holds the contents' own operations.
 */
@ExtendWith(MockitoExtension.class)
class CrucibleBlockEntityTest {

    // -- GooValue.meltPayout ---------------------------------------------

    /**
     * Empty GooValue produces EMPTY GooContents.
     */
    @Test
    void emptyGooValueProducesEmptyGooContents() {
        GooContents result = GooValue.EMPTY.meltPayout();
        assertTrue(result.isEmpty());
    }

    /**
     * Single-type GooValue uses int correctly.
     */
    @Test
    void singleTypeGooValueConvertsToGooContents() {
        GooValue value = new GooValue(Map.of(GooTypes.ROCK, 500));
        GooContents result = value.meltPayout();
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
                GooTypes.JELLY, 50
        ));
        GooContents result = value.meltPayout();
        assertEquals(100, result.getVolume(GooTypes.ROCK));
        assertEquals(250, result.getVolume(GooTypes.METAL));
        assertEquals(50, result.getVolume(GooTypes.JELLY));
        assertEquals(3, result.typeCount());
    }
}
