package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the pool-plus-reservoir total the crucible renderer draws its surface
 * for, past the int range (decision diagnose-then-fix-crucible-overflow).
 * Before the fix the renderer summed the two as int: this fill read sum
 * -2094967296 and fill 0.0, and the surface was never drawn.
 */
class CrucibleHeldVolumeTest {

    /**
     * A reservoir of two types past 2B plus a melting pool reads the whole
     * positive total, and the fill drawn from it is above zero.
     */
    @Test
    void totalPastIntRangeReadsPositiveAndFills() {
        GooContents reservoir = new GooContents(Map.of(
                GooTypes.ROCK, 1_100_000_000, GooTypes.METAL, 1_000_000_000));
        GooContents pool = new GooContents(Map.of(GooTypes.ROCK, 100_000_000));

        long held = CrucibleBasin.heldVolume(pool.totalVolume(), reservoir.totalVolume());

        assertEquals(2_200_000_000L, held);
        assertTrue(CrucibleBasin.fillFraction(held) > 0f);
    }
}
