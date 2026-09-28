package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster.Prism;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quartz cluster's geometry (decision crystallizer-emits-chrysm): prisms at
 * multiples of 22.5 degrees, growing at one rate through every tier at the
 * crystallizer's pace, each tier larger than the last, nothing for nothing
 * crystallized, and the client's easing closing on its target smoothly.
 */
class CrystalClusterTest {

    /** The steadiness is read over 10-tick windows, since single ticks crystallize whole 10 mB steps. */
    private static final int WINDOW = 10;
    private static final double STEADINESS = 0.1;
    private static final long[] VOLUMES = {1, 10, 100, 1_000, 10_000, 250_000, 1_000_000, 50_000_000,
        1_000_000_000L};

    @Test
    void nothingCrystallizedGrowsNothing() {
        assertTrue(CrystalCluster.prisms(0).isEmpty());
        assertEquals(0, CrystalCluster.reach(0)[1]);
    }

    @Test
    void everyPrismTiltsAndTurnsInStepsOfTwentyTwoAndAHalf() {
        for (Prism prism : CrystalCluster.prisms(ChrysmTier.MEGACHRYSM.volume())) {
            assertEquals(0, prism.tilt() % CrystalCluster.ANGLE_STEP, 1e-9, prism.toString());
            assertEquals(0, prism.yaw() % CrystalCluster.ANGLE_STEP, 1e-9, prism.toString());
        }
    }

    @Test
    void theClusterNeverShrinksAsGooCrystallizes() {
        double previous = 0;
        int previousCount = 0;
        for (long volume : VOLUMES) {
            List<Prism> prisms = CrystalCluster.prisms(volume);
            double total = prisms.stream().mapToDouble(Prism::length).sum();
            assertTrue(total >= previous, volume + " mB grew " + total + " after " + previous);
            assertTrue(prisms.size() >= previousCount, volume + " mB lost a prism");
            previous = total;
            previousCount = prisms.size();
        }
    }

    @Test
    void eachTierStandsLargerThanTheOneBelow() {
        double chrysm = CrystalCluster.reach(ChrysmTier.CHRYSM.volume())[1];
        double kilo = CrystalCluster.reach(ChrysmTier.KILOCHRYSM.volume())[1];
        double mega = CrystalCluster.reach(ChrysmTier.MEGACHRYSM.volume())[1];
        assertTrue(chrysm > 0 && kilo > chrysm && mega > kilo, chrysm + " < " + kilo + " < " + mega);
        assertTrue(CrystalCluster.prisms(ChrysmTier.MEGACHRYSM.volume()).size()
                > CrystalCluster.prisms(ChrysmTier.CHRYSM.volume()).size(), "a larger tier grows more prisms");
    }

    @Test
    void growthStopsAtAMegachrysm() {
        assertEquals(1.0, CrystalCluster.growth(ChrysmTier.MEGACHRYSM.volume()), 1e-9);
        assertEquals(1.0, CrystalCluster.growth(Long.MAX_VALUE), 1e-9);
    }

    @Test
    void thePaceGrowsTheCrystalAtOneRateThroughEveryTier() {
        long crystallized = 0;
        double budget = 0;
        int ticks = CrystallizerPhases.TICKS_PER_TIER * ChrysmTier.values().length;
        double[] growth = new double[ticks + 1];
        for (int tick = 1; tick <= ticks; tick++) {
            budget = CrystallizerPhases.nextBudget(budget, crystallized);
            long steps = (long) (budget / CrystallizerPhases.GOO_PER_CRYSTAL);
            crystallized += steps * CrystallizerPhases.GOO_PER_CRYSTAL;
            budget -= steps * CrystallizerPhases.GOO_PER_CRYSTAL;
            growth[tick] = CrystalCluster.growth(Math.min(crystallized, ChrysmTier.MEGACHRYSM.volume()));
        }
        double mean = growth[ticks - WINDOW] / (ticks - WINDOW);
        for (int tick = WINDOW; tick + WINDOW < ticks; tick += WINDOW) {
            double rate = (growth[tick + WINDOW] - growth[tick]) / WINDOW;
            assertEquals(mean, rate, mean * STEADINESS, "growth rate over ticks " + tick + " to " + (tick + WINDOW));
        }
    }

    @Test
    void easingClosesOnTheTargetWithoutOvershoot() {
        double displayed = 0;
        double previous;
        for (int tick = 0; tick < 50; tick++) {
            previous = displayed;
            displayed = CrystalCluster.ease(displayed, 0.5);
            assertTrue(displayed >= previous && displayed <= 0.5, "tick " + tick + " drew " + displayed);
        }
        assertEquals(0.5, displayed, 1e-3);
    }

    @Test
    void easingSnapsDownWhenAChrysmIsTaken() {
        assertEquals(0.1, CrystalCluster.ease(0.6, 0.1), 1e-9);
    }
}
