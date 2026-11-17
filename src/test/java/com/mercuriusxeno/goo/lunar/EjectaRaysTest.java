package com.mercuriusxeno.goo.lunar;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fresh crater's ejecta rays each run at their own angle, width and length: a column on a ray's line reads on it,
 * a column midway between two rays, past the ray's own end, or inside the crater reads off every ray; two craters'
 * rays differ; and the splatter flecks fewer of a ray's columns the further along it they lie.
 * decision lunar-surface-gains-giant-craters-mountains-rilles-and-boulder-fields
 */
class EjectaRaysTest {

    private static final EjectaRays.Ray NORTH_EAST = new EjectaRays.Ray(0.4, 0.1, 2.0);
    private static final EjectaRays.Ray SOUTH_WEST = new EjectaRays.Ray(0.4 + Math.PI, 0.1, 2.4);
    private static final List<EjectaRays.Ray> RAYS = List.of(NORTH_EAST, SOUTH_WEST);
    /** A fifth of a radius out past the rim, where every ray still runs wide. */
    private static final double NEAR_THE_RIM = 1.2;
    /** The splatter is counted over this many columns at each end of a ray. */
    private static final int SPLATTER_SAMPLE = 64;

    @Test
    void aColumnOnARaysLineReadsOnTheRay() {
        assertTrue(EjectaRays.along(NORTH_EAST.angle(), NEAR_THE_RIM, RAYS) >= 0.0);
    }

    @Test
    void aColumnMidwayBetweenTwoRaysReadsOffEveryRay() {
        assertTrue(EjectaRays.along(NORTH_EAST.angle() + Math.PI / 2.0, NEAR_THE_RIM, RAYS) < 0.0);
    }

    @Test
    void aColumnPastARaysOwnEndReadsOffThatRay() {
        assertTrue(EjectaRays.along(NORTH_EAST.angle(), NORTH_EAST.length() + 0.1, RAYS) < 0.0);
        assertTrue(EjectaRays.along(SOUTH_WEST.angle(), NORTH_EAST.length() + 0.1, RAYS) >= 0.0);
    }

    @Test
    void aColumnInsideTheCraterReadsOffEveryRay() {
        assertTrue(EjectaRays.along(NORTH_EAST.angle(), 0.5, RAYS) < 0.0);
    }

    @Test
    void twoCratersThrowRaysOfTheirOwn() {
        List<EjectaRays.Ray> first = EjectaRays.rays(0.3, 0.0, roll -> (roll * 0.37) % 1.0);
        List<EjectaRays.Ray> second = EjectaRays.rays(0.3, 0.0, roll -> (roll * 0.61 + 0.2) % 1.0);
        assertNotEquals(first, second);
    }

    @Test
    void theSplatterThinsAlongTheRay() {
        int nearTheRim = 0;
        int nearTheEnd = 0;
        for (int x = 0; x < SPLATTER_SAMPLE; x++) {
            for (int z = 0; z < SPLATTER_SAMPLE; z++) {
                nearTheRim += EjectaRays.flecks(x, z, 0.0) ? 1 : 0;
                nearTheEnd += EjectaRays.flecks(x, z, 0.95) ? 1 : 0;
            }
        }
        assertTrue(nearTheRim > nearTheEnd, "near the rim " + nearTheRim + ", near the end " + nearTheEnd);
        assertTrue(nearTheRim < SPLATTER_SAMPLE * SPLATTER_SAMPLE, "the splatter flecks every column near the rim");
    }

    @Test
    void noRayFlecksAColumnOutsideItsCrater() {
        assertFalse(EjectaRays.isOnRayOf(0, 0, 0.0, 0.0, 10.0, RAYS));
    }
}
