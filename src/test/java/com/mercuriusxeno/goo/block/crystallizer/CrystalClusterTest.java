package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster.Prism;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quartz cluster's geometry (decision crystallizer-emits-chrysm): prisms at
 * multiples of 22.5 degrees, growing a quarter per tier evenly with the goo inside
 * it, each cluster tier larger than the last, compressing through materia into a
 * marble (operator ruling), nothing for nothing crystallized, and the client's
 * easing closing on its target smoothly.
 */
class CrystalClusterTest {

    private static final long[] VOLUMES = {1, 10, 100, 1_000, 10_000, 250_000, 1_000_000, 20_000_000,
        32_000_000};
    private static final double QUARTER = 0.25;

    @Test
    void nothingCrystallizedGrowsNothing() {
        assertTrue(CrystalCluster.prisms(0).isEmpty());
        assertEquals(0, CrystalCluster.reach(0)[1]);
    }

    @Test
    void everyPrismTiltsAndTurnsInStepsOfTwentyTwoAndAHalf() {
        for (Prism prism : CrystalCluster.prisms(ChrysmTier.FLOWERING_CHRYSM.volume())) {
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
    void eachClusterTierStandsLargerThanTheOneBelow() {
        double previous = 0;
        for (ChrysmTier tier : List.of(ChrysmTier.CHRYSM, ChrysmTier.BUDDING_CHRYSM, ChrysmTier.FLOWERING_CHRYSM)) {
            double height = CrystalCluster.reach(tier.volume())[1];
            assertTrue(height > previous, tier + " stands " + height + " over " + previous);
            previous = height;
        }
        assertTrue(CrystalCluster.prisms(ChrysmTier.FLOWERING_CHRYSM.volume()).size()
                > CrystalCluster.prisms(ChrysmTier.CHRYSM.volume()).size(), "a larger tier grows more prisms");
    }

    @Test
    void growthStopsAtMateria() {
        assertEquals(1.0, CrystalCluster.growth(ChrysmTier.MATERIA.volume()), 1e-9);
        assertEquals(1.0, CrystalCluster.growth(Long.MAX_VALUE), 1e-9);
    }

    @Test
    void eachTierGrowsAQuarterEvenlyWithItsGoo() {
        assertEquals(0.5 * QUARTER, CrystalCluster.growth(16_000), 1e-9);
        assertEquals(QUARTER, CrystalCluster.growth(ChrysmTier.CHRYSM.volume()), 1e-9);
        assertEquals(1.5 * QUARTER, CrystalCluster.growth(516_000), 1e-9);
        assertEquals(2 * QUARTER, CrystalCluster.growth(ChrysmTier.BUDDING_CHRYSM.volume()), 1e-9);
        assertEquals(2.5 * QUARTER, CrystalCluster.growth(16_500_000), 1e-9);
        assertEquals(3 * QUARTER, CrystalCluster.growth(ChrysmTier.FLOWERING_CHRYSM.volume()), 1e-9);
        assertEquals(3.5 * QUARTER, CrystalCluster.growth(516_000_000), 1e-9);
    }

    @Test
    void theClusterStandsWholeAndFlatFacedAtFlowering() {
        List<Prism> prisms = CrystalCluster.prisms(3 * QUARTER);
        assertEquals(7, prisms.size(), "every bud stands at flowering");
        assertEquals(0, prisms.getFirst().rounding(), 1e-9);
    }

    @Test
    void throughMateriaTheBudsRetractAsTheCentralPrismRoundsAndShrinks() {
        List<Prism> full = CrystalCluster.prisms(3 * QUARTER);
        List<Prism> half = CrystalCluster.prisms(3.5 * QUARTER);
        assertEquals(0.5, half.getFirst().rounding(), 1e-9, "the central prism half rounded");
        assertTrue(half.getFirst().length() < full.getFirst().length(), "the central prism shrinks");
        for (int i = 1; i < full.size(); i++) {
            assertEquals(full.get(i).length() / 2, half.get(i).length(), 1e-9, "bud " + i + " half retracted");
        }
    }

    @Test
    void atMateriaOnlyTheMarbleStands() {
        List<Prism> prisms = CrystalCluster.prisms(ChrysmTier.MATERIA.volume());
        assertEquals(1, prisms.size(), "the buds are gone");
        Prism marble = prisms.getFirst();
        assertEquals(1, marble.rounding(), 1e-9);
        assertEquals(CrystalCluster.ORB_RADIUS, marble.radius(), 1e-9);
        assertEquals(2 * CrystalCluster.ORB_RADIUS, marble.length(), 1e-9);
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
