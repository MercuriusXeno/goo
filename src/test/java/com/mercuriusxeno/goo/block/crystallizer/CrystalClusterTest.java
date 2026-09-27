package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster.Prism;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quartz cluster's geometry (decision crystallizer-emits-chrysm): prisms at
 * multiples of 22.5 degrees, growing steadily with the crystallized volume, each
 * tier larger than the last, and nothing for nothing crystallized.
 */
class CrystalClusterTest {

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
}
