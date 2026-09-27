package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Chamber;
import com.mercuriusxeno.goo.item.ChrysmTier;
import com.mercuriusxeno.goo.item.GooContents;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystallizer's phase arithmetic (decision crystallizer-emits-chrysm): the
 * operator's n, cube-root crystal cost and log phase times, the dial's stop, what
 * the holding takes, and what each phase spends.
 */
class CrystallizerPhasesTest {

    private static final int CHRYSM = 1_000;
    private static final int KILO = 1_000_000;
    private static final int COST = 10;
    private static final int KILO_COST = 100;
    private static final long TIER_RATIO = 1_000L;

    private static Chamber empty(ChrysmTier dial) {
        return new Chamber(GooContents.EMPTY, null, null, dial);
    }

    private static Chamber holding(GooContents held, ChrysmTier dial) {
        return new Chamber(held, null, null, dial);
    }

    private static Chamber formedEnder(GooContents held, ChrysmTier formed, ChrysmTier dial) {
        return new Chamber(held, formed, GooTypes.ENDER, dial);
    }

    @Test
    void crystalCostIsTheCubeRootOfTheTierVolume() {
        assertEquals(COST, CrystallizerPhases.crystalCost(ChrysmTier.CHRYSM));
        assertEquals(KILO_COST, CrystallizerPhases.crystalCost(ChrysmTier.KILOCHRYSM));
        assertEquals(1_000, CrystallizerPhases.crystalCost(ChrysmTier.MEGACHRYSM));
    }

    @Nested
    class PhaseTicks {

        @Test
        void phasesTakeTwoFourAndSixHundredTicks() {
            assertEquals(200, CrystallizerPhases.phaseTicks(ChrysmTier.CHRYSM));
            assertEquals(400, CrystallizerPhases.phaseTicks(ChrysmTier.KILOCHRYSM));
            assertEquals(600, CrystallizerPhases.phaseTicks(ChrysmTier.MEGACHRYSM));
        }

        @Test
        void eachPhaseTakesUnderAThousandTimesTheOneBelow() {
            assertTrue(CrystallizerPhases.phaseTicks(ChrysmTier.KILOCHRYSM)
                    < TIER_RATIO * CrystallizerPhases.phaseTicks(ChrysmTier.CHRYSM));
            assertTrue(CrystallizerPhases.phaseTicks(ChrysmTier.MEGACHRYSM)
                    < TIER_RATIO * CrystallizerPhases.phaseTicks(ChrysmTier.KILOCHRYSM));
        }
    }

    @Nested
    class Goal {

        @Test
        void nothingFormedFormsAChrysmAtAnyDial() {
            assertEquals(ChrysmTier.CHRYSM, CrystallizerPhases.goal(empty(ChrysmTier.CHRYSM)));
        }

        @Test
        void aFormedTierBelowTheDialAdvances() {
            assertEquals(ChrysmTier.KILOCHRYSM,
                    CrystallizerPhases.goal(formedEnder(GooContents.EMPTY, ChrysmTier.CHRYSM, ChrysmTier.KILOCHRYSM)));
            assertEquals(ChrysmTier.MEGACHRYSM,
                    CrystallizerPhases.goal(formedEnder(GooContents.EMPTY, ChrysmTier.KILOCHRYSM, ChrysmTier.MEGACHRYSM)));
        }

        @Test
        void aFormedTierAtOrPastTheDialHolds() {
            assertNull(CrystallizerPhases.goal(formedEnder(GooContents.EMPTY, ChrysmTier.CHRYSM, ChrysmTier.CHRYSM)));
            assertNull(CrystallizerPhases.goal(formedEnder(GooContents.EMPTY, ChrysmTier.KILOCHRYSM, ChrysmTier.CHRYSM)));
            assertNull(CrystallizerPhases.goal(formedEnder(GooContents.EMPTY, ChrysmTier.MEGACHRYSM, ChrysmTier.MEGACHRYSM)));
        }
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
        void aFormedChrysmNamesTheType() {
            assertEquals(GooTypes.ENDER, CrystallizerPhases.formingType(
                    formedEnder(GooContents.EMPTY, ChrysmTier.CHRYSM, ChrysmTier.KILOCHRYSM)));
        }
    }

    @Nested
    class Capacity {

        private final GooContents holdingEnder = GooContents.EMPTY.withAdded(GooTypes.ENDER, 1);

        @Test
        void theFirstPhaseTakesAChrysmVolumeAndItsCrystal() {
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(holding(holdingEnder, ChrysmTier.CHRYSM), GooTypes.ENDER));
            assertEquals(COST, CrystallizerPhases.capacityFor(holding(holdingEnder, ChrysmTier.CHRYSM), GooTypes.CRYSTAL));
        }

        @Test
        void aThirdTypeIsRefused() {
            assertEquals(0, CrystallizerPhases.capacityFor(holding(holdingEnder, ChrysmTier.CHRYSM), GooTypes.ROCK));
        }

        @Test
        void anEmptyHoldingTakesAnyTypeAndCrystalForItself() {
            assertEquals(CHRYSM, CrystallizerPhases.capacityFor(empty(ChrysmTier.CHRYSM), GooTypes.ROCK));
            assertEquals(CHRYSM + COST, CrystallizerPhases.capacityFor(empty(ChrysmTier.CHRYSM), GooTypes.CRYSTAL));
        }

        @Test
        void aChrysmAdvancingTakesTheGooBetweenTiersAndThePhaseCrystal() {
            Chamber chamber = formedEnder(GooContents.EMPTY, ChrysmTier.CHRYSM, ChrysmTier.KILOCHRYSM);
            assertEquals(KILO - CHRYSM, CrystallizerPhases.capacityFor(chamber, GooTypes.ENDER));
            assertEquals(KILO_COST, CrystallizerPhases.capacityFor(chamber, GooTypes.CRYSTAL));
            assertEquals(0, CrystallizerPhases.capacityFor(chamber, GooTypes.ROCK));
        }

        @Test
        void aChrysmHeldAtTheDialTakesNothing() {
            Chamber chamber = formedEnder(GooContents.EMPTY, ChrysmTier.CHRYSM, ChrysmTier.CHRYSM);
            assertEquals(0, CrystallizerPhases.capacityFor(chamber, GooTypes.ENDER));
            assertEquals(0, CrystallizerPhases.capacityFor(chamber, GooTypes.CRYSTAL));
        }
    }

    @Nested
    class Forming {

        @Test
        void aChrysmVolumeAndThePhaseCrystalSpendBoth() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM).withAdded(GooTypes.CRYSTAL, COST);
            assertEquals(held, CrystallizerPhases.spentToForm(holding(held, ChrysmTier.CHRYSM)));
        }

        @Test
        void missingCrystalOrGooIsNotReady() {
            GooContents shortCrystal = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM)
                    .withAdded(GooTypes.CRYSTAL, COST - 1);
            GooContents shortGoo = GooContents.EMPTY.withAdded(GooTypes.ENDER, CHRYSM - 1)
                    .withAdded(GooTypes.CRYSTAL, COST);
            assertNull(CrystallizerPhases.spentToForm(holding(shortCrystal, ChrysmTier.CHRYSM)));
            assertNull(CrystallizerPhases.spentToForm(holding(shortGoo, ChrysmTier.CHRYSM)));
        }

        @Test
        void crystalAloneFormsFromAChrysmVolumeAndItsCost() {
            GooContents short1 = GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, CHRYSM + COST - 1);
            GooContents enough = GooContents.EMPTY.withAdded(GooTypes.CRYSTAL, CHRYSM + COST);
            assertNull(CrystallizerPhases.spentToForm(holding(short1, ChrysmTier.CHRYSM)));
            assertEquals(enough, CrystallizerPhases.spentToForm(holding(enough, ChrysmTier.CHRYSM)));
        }

        @Test
        void theKilochrysmPhaseSpendsTheGooBetweenTiersAndItsCrystal() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, KILO - CHRYSM)
                    .withAdded(GooTypes.CRYSTAL, KILO_COST);
            assertEquals(held, CrystallizerPhases.spentToForm(
                    formedEnder(held, ChrysmTier.CHRYSM, ChrysmTier.KILOCHRYSM)));
        }

        @Test
        void aChamberHeldAtTheDialSpendsNothing() {
            GooContents held = GooContents.EMPTY.withAdded(GooTypes.ENDER, KILO).withAdded(GooTypes.CRYSTAL, KILO_COST);
            assertNull(CrystallizerPhases.spentToForm(formedEnder(held, ChrysmTier.CHRYSM, ChrysmTier.CHRYSM)));
        }
    }

    @Test
    void theDialWrapsFromThreeToOne() {
        assertEquals(2, CrystallizerPhases.nextDial(1));
        assertEquals(3, CrystallizerPhases.nextDial(2));
        assertEquals(1, CrystallizerPhases.nextDial(3));
    }
}
