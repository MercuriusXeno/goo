package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystallizer's phase arithmetic (decision crystallizer-emits-chrysm): the
 * operator's n and cube-root crystal cost, what the holding takes, and what
 * forming a chrysm spends.
 */
class CrystallizerPhasesTest {

    private static final int CHRYSM = 1_000;
    private static final int COST = 10;

    @Test
    void chrysmFormsInTwoHundredTicks() {
        assertEquals(200, CrystallizerPhases.CHRYSM_TICKS);
    }

    @Test
    void crystalCostIsTheCubeRootOfTheTierVolume() {
        assertEquals(10, CrystallizerPhases.crystalCost(ChrysmTier.CHRYSM));
        assertEquals(100, CrystallizerPhases.crystalCost(ChrysmTier.KILOCHRYSM));
        assertEquals(1_000, CrystallizerPhases.crystalCost(ChrysmTier.MEGACHRYSM));
    }

    @Nested
    class FormingType {

        @Test
        void theTypeBesideCrystalForms() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, COST).withAdded(GooTypes.ENDER, 1);
            assertEquals(GooTypes.ENDER, CrystallizerPhases.formingType(held));
        }

        @Test
        void crystalFormsWhenItIsAllTheHolding() {
            assertEquals(GooTypes.CRYSTAL, CrystallizerPhases.formingType(GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, 1)));
        }

        @Test
        void anEmptyHoldingFormsNothing() {
            assertNull(CrystallizerPhases.formingType(GooContents.EMPTY));
        }
    }

    @Nested
    class Capacity {

        private final GooContents holdingEnder = GooContents.EMPTY.withAdded(GooTypes.ENDER, 1);

        @Test
        void theFormingTypeTakesAChrysmVolume() {
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(holdingEnder, null, GooTypes.ENDER));
        }

        @Test
        void crystalBesideAnotherTypeTakesThePhaseCost() {
            assertEquals(COST, CrystallizerPhases.capacityFor(holdingEnder, null, GooTypes.CRYSTAL));
        }

        @Test
        void aThirdTypeIsRefused() {
            assertEquals(0, CrystallizerPhases.capacityFor(holdingEnder, null, GooTypes.ROCK));
        }

        @Test
        void anEmptyHoldingTakesAnyTypeAndCrystalForItself() {
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(GooContents.EMPTY, null, GooTypes.ROCK));
            assertEquals(CHRYSM + COST, CrystallizerPhases.capacityFor(GooContents.EMPTY, null, GooTypes.CRYSTAL));
        }

        @Test
        void aFormedChrysmTakesNothingMore() {
            assertEquals(0, CrystallizerPhases.capacityFor(holdingEnder, ChrysmTier.CHRYSM, GooTypes.ENDER));
            assertEquals(0, CrystallizerPhases.capacityFor(holdingEnder, ChrysmTier.CHRYSM, GooTypes.CRYSTAL));
        }
    }

    @Nested
    class Forming {

        @Test
        void aChrysmVolumeAndThePhaseCrystalSpendBoth() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM).withAdded(GooTypes.CRYSTAL, COST);
            assertEquals(held, CrystallizerPhases.spentToForm(held));
            assertTrue(CrystallizerPhases.readyToForm(held));
        }

        @Test
        void missingCrystalIsNotReady() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM).withAdded(GooTypes.CRYSTAL, COST - 1);
            assertFalse(CrystallizerPhases.readyToForm(held));
        }

        @Test
        void missingGooIsNotReady() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM - 1).withAdded(GooTypes.CRYSTAL, COST);
            assertFalse(CrystallizerPhases.readyToForm(held));
        }

        @Test
        void crystalAloneFormsFromAChrysmVolumeAndItsCost() {
            assertFalse(CrystallizerPhases.readyToForm(GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, CHRYSM + COST - 1)));
            assertTrue(CrystallizerPhases.readyToForm(GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, CHRYSM + COST)));
        }
    }
}
