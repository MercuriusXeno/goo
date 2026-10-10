package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispsStep: a cell is dark enough for a wisp only under the threshold, a
 * picked cell lies within the reach of its center, an edge pick lies in the
 * band at the reach's edge, a radius of zero picks the center itself, as the
 * drip does, and the channel's zone grows each held tick up to its radius.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 */
class WispsStepTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -5);
    private static final int THRESHOLD = 8;
    private static final int PICKS = 500;
    private static final double REACH = 8;
    /** Rounding a pick to its cell moves it by at most this, half a cell's diagonal. */
    private static final double ROUNDING = Math.sqrt(3) / 2;
    private static final WispsStep CHANNEL = new WispsStep(32, 16, 1200, 1, true, 0.5, true);

    @Test
    void aCellIsDarkOnlyUnderTheThreshold() {
        assertTrue(WispsStep.isDark(THRESHOLD - 1, THRESHOLD));
        assertFalse(WispsStep.isDark(THRESHOLD, THRESHOLD));
    }

    @Test
    void everyPickLiesWithinTheReach() {
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < PICKS; i++) {
            BlockPos pick = WispsStep.pick(CENTER, REACH, random);
            assertTrue(distance(pick) <= REACH + ROUNDING, pick + " lies past the reach");
        }
    }

    @Test
    void everyEdgePickLiesInTheEdgeBand() {
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < PICKS; i++) {
            double distance = distance(WispsStep.pickNearTheEdge(CENTER, REACH, random));
            assertTrue(distance <= REACH + ROUNDING, distance + " lies past the reach");
            assertTrue(distance >= REACH - WispsStep.EDGE_BAND - ROUNDING, distance + " lies inside the edge band");
        }
    }

    @Test
    void aZeroReachPicksTheCenter() {
        assertEquals(CENTER, WispsStep.pick(CENTER, 0, RandomSource.create(7L)));
        assertEquals(CENTER, WispsStep.pickNearTheEdge(CENTER, 0, RandomSource.create(7L)));
    }

    @Test
    void theZoneGrowsEachHeldTickUpToItsRadius() {
        assertEquals(WispsStep.ZONE_START, CHANNEL.zoneRadius(1));
        assertEquals(WispsStep.ZONE_START + 0.5 * 20, CHANNEL.zoneRadius(21));
        assertEquals(32, CHANNEL.zoneRadius(1000));
    }

    @Test
    void aZoneWithoutGrowthHoldsAtItsRadius() {
        WispsStep drip = new WispsStep(0, 1, 1200, 1, false, 0, false);
        assertEquals(0, drip.zoneRadius(1));
        assertEquals(0, drip.zoneRadius(500));
    }

    private static double distance(BlockPos pick) {
        return Math.sqrt(pick.distSqr(CENTER));
    }
}
