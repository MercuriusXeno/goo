package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Held;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Roles;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Step;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The crystallizer's arithmetic (decision crystallizer-emits-chrysm): whichever
 * canister holds crystal is the catalyst and the other's goo grows; goo
 * crystallizes at 10% crystal, pauses without crystal, stops at the knob's tier,
 * and the item inside is the highest tier reached.
 */
class CrystallizerPhasesTest {

    private static final int CHRYSM = 1_000;
    private static final Held ENDER = new Held(GooTypes.ENDER, CHRYSM);
    private static final Held CRYSTAL = new Held(GooTypes.CRYSTAL, 100);

    @Nested
    class ChoosingRoles {

        @Test
        void crystalInEitherSlotIsTheCatalyst() {
            assertEquals(new Roles(0, 1), CrystallizerPhases.roles(CRYSTAL, ENDER));
            assertEquals(new Roles(1, 0), CrystallizerPhases.roles(ENDER, CRYSTAL));
        }

        @Test
        void twoCrystalCanistersGrowCrystalFromTheSecond() {
            assertEquals(new Roles(0, 1), CrystallizerPhases.roles(CRYSTAL, CRYSTAL));
        }

        @Test
        void noCrystalOrNoIngredientGrowsNothing() {
            assertNull(CrystallizerPhases.roles(ENDER, new Held(GooTypes.ROCK, CHRYSM)));
            assertNull(CrystallizerPhases.roles(CRYSTAL, Held.NOTHING));
            assertNull(CrystallizerPhases.roles(Held.NOTHING, ENDER));
        }
    }

    @Nested
    class Crystallizing {

        @Test
        void goodCrystalCrystallizesAllTheGooAtOneTenth() {
            assertEquals(new Step(GooTypes.ENDER, CHRYSM, 100),
                    CrystallizerPhases.step(ENDER, CRYSTAL, 0, null, ChrysmTier.CHRYSM));
        }

        @Test
        void shortCrystalCrystallizesWhatItPaysFor() {
            assertEquals(new Step(GooTypes.ENDER, 500, 50),
                    CrystallizerPhases.step(ENDER, new Held(GooTypes.CRYSTAL, 50), 0, null, ChrysmTier.CHRYSM));
        }

        @Test
        void noCrystalPauses() {
            assertNull(CrystallizerPhases.step(ENDER, Held.NOTHING, 0, null, ChrysmTier.CHRYSM));
        }

        @Test
        void gooUnderOneStepWaits() {
            assertNull(CrystallizerPhases.step(new Held(GooTypes.ENDER, 9), CRYSTAL, 0, null, ChrysmTier.CHRYSM));
        }

        @Test
        void crystallizingStopsAtTheKnobTier() {
            assertEquals(new Step(GooTypes.ENDER, 400, 40),
                    CrystallizerPhases.step(ENDER, CRYSTAL, 600, GooTypes.ENDER, ChrysmTier.CHRYSM));
            assertNull(CrystallizerPhases.step(ENDER, CRYSTAL, CHRYSM, GooTypes.ENDER, ChrysmTier.CHRYSM));
        }

        @Test
        void anotherTypeWaitsWhileOneIsCrystallized() {
            assertNull(CrystallizerPhases.step(new Held(GooTypes.ROCK, CHRYSM), CRYSTAL, 500, GooTypes.ENDER,
                    ChrysmTier.KILOCHRYSM));
        }
    }

    @Test
    void theItemInsideIsTheHighestTierReached() {
        assertNull(CrystallizerPhases.reachedTier(CHRYSM - 1));
        assertEquals(ChrysmTier.CHRYSM, CrystallizerPhases.reachedTier(999_999));
        assertEquals(ChrysmTier.KILOCHRYSM, CrystallizerPhases.reachedTier(1_000_000));
        assertEquals(ChrysmTier.MEGACHRYSM, CrystallizerPhases.reachedTier(1_000_000_000L));
    }

    @Test
    void theKnobWrapsFromThreeToOne() {
        assertEquals(2, CrystallizerPhases.nextKnob(1));
        assertEquals(3, CrystallizerPhases.nextKnob(2));
        assertEquals(1, CrystallizerPhases.nextKnob(3));
    }
}
