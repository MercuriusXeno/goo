package com.mercuriusxeno.goo.ability.program;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RayStep's cadence and a prism's split: the hold's first tick hits and then
 * one in every ten; a refracted hit is raised by its factor and thinned by
 * the target count to its exponent, costing each target only a little.
 * decision sunbeam-splits-at-the-prism-with-a-glisten
 */
class RayStepTest {

    private static final RayStep.Refraction SUNBEAM = new RayStep.Refraction(16, 1.5, 0.85);
    private static final RayStep RAY = new RayStep(32, 10, List.of(), SUNBEAM, List.of());
    private static final double TOLERANCE = 1e-9;

    @Test
    void hitsOnTheFirstTickThenEveryTenth() {
        assertTrue(RAY.hitsOn(1));
        assertFalse(RAY.hitsOn(2));
        assertFalse(RAY.hitsOn(10));
        assertTrue(RAY.hitsOn(11));
    }

    @Test
    void oneTargetTakesTheWholeRaisedHit() {
        assertEquals(1.5, SUNBEAM.share(1), TOLERANCE);
    }

    @Test
    void eachOfNTargetsTakesTheRaisedHitOverNToTheExponent() {
        assertEquals(1.5 / Math.pow(3, 0.85), SUNBEAM.share(3), TOLERANCE);
    }

    @Test
    void theSplitThinsSlowerThanAnEvenOne() {
        assertTrue(SUNBEAM.share(3) * 3 > SUNBEAM.share(1));
        assertTrue(SUNBEAM.share(3) > 1.5 / 3);
    }

    @Test
    void noTargetTakesNothing() {
        assertEquals(0, SUNBEAM.share(0), TOLERANCE);
    }
}
