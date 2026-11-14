package com.mercuriusxeno.goo.throwing;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream's drips scatter: each tick throws as many drips as its pace names,
 * each at its own place in the cone, the same places for the same tick of a
 * hold and others the next, spread evenly over the whole cone's area, so the
 * goo spreads rather than stacking on a fixed fan's spots or on the aim line
 * (operator ruling 2026-11-14; decision goop-drains-per-cubic-meter-of-slime-placed, the spray's pace).
 */
class CoatRayTest {

    /** Ticks of a long hold, enough drips to read the scatter's spread. */
    private static final int LONG_HOLD = 2000;
    private static final int DRIPS_A_TICK = 2;

    @Test
    void aTickThrowsTheDripsItsPaceNames() {
        assertEquals(DRIPS_A_TICK, CoatRay.scatteredShares(1, DRIPS_A_TICK).size());
    }

    @Test
    void theSameTickOfAHoldThrowsTheSamePlaces() {
        assertEquals(CoatRay.scatteredShares(7, DRIPS_A_TICK), CoatRay.scatteredShares(7, DRIPS_A_TICK));
    }

    @Test
    void theNextTickThrowsOtherPlaces() {
        assertNotEquals(CoatRay.scatteredShares(7, DRIPS_A_TICK), CoatRay.scatteredShares(8, DRIPS_A_TICK));
    }

    /**
     * Drips spread evenly over the cone's area: a quarter of them land
     * inside half the cone's radius, a quarter of its area, so no cell near
     * the aim line takes more than its share, and every drip stays inside the
     * cone (operator ruling 2026-11-14: stop hitting the same block).
     */
    @Test
    void aHoldsDripsSpreadEvenlyOverTheConesArea() {
        List<CoatRay.ConeShare> thrown = new ArrayList<>();
        for (int held = 1; held <= LONG_HOLD; held++) {
            thrown.addAll(CoatRay.scatteredShares(held, DRIPS_A_TICK));
        }
        double nearTheAxis = thrown.stream().filter(share -> share.tilt() < 0.5).count() / (double) thrown.size();

        assertTrue(Math.abs(nearTheAxis - 0.25) < 0.03, "a quarter of the drips should land inside half the cone's radius, " + nearTheAxis);
        assertTrue(thrown.stream().allMatch(share -> share.tilt() >= 0 && share.tilt() < 1),
                "every drip should stay inside the cone");
    }
}
