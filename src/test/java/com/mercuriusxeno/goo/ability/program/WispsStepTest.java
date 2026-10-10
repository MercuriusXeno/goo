package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WispsStep: a cell is dark enough for a wisp only under the threshold, a
 * picked cell lies within the radius of its center, and a radius of zero
 * picks the center itself, as the drip does.
 * decisions radiant-wisps-where-light-is-low, radiant-drip-places-a-wisp
 */
class WispsStepTest {

    private static final BlockPos CENTER = new BlockPos(10, 64, -5);
    private static final int THRESHOLD = 8;
    private static final int PICKS = 500;

    @Test
    void aCellIsDarkOnlyUnderTheThreshold() {
        assertTrue(WispsStep.isDark(THRESHOLD - 1, THRESHOLD));
        assertFalse(WispsStep.isDark(THRESHOLD, THRESHOLD));
    }

    @Test
    void everyPickLiesWithinTheRadius() {
        WispsStep wisps = new WispsStep(8, 2, 1200, 0, false);
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < PICKS; i++) {
            BlockPos pick = wisps.pick(CENTER, random);
            assertTrue(pick.distSqr(CENTER) <= 9 * 9, pick + " lies past the radius");
        }
    }

    @Test
    void aZeroRadiusPicksTheCenter() {
        assertEquals(CENTER, new WispsStep(0, 1, 1200, 1, false).pick(CENTER, RandomSource.create(7L)));
    }
}
