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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystallizer's arithmetic (decision crystallizer-emits-chrysm): whichever
 * canister holds crystal is the catalyst and the other's goo grows; goo
 * crystallizes at 10% crystal, pauses without crystal, stops at the knob's tier,
 * and the item inside is the highest tier reached.
 */
class CrystallizerPhasesTest {

    private static final int CHRYSM = (int) ChrysmTier.CHRYSM.volume();
    private static final int CHRYSM_CRYSTAL = CHRYSM / CrystallizerPhases.GOO_PER_CRYSTAL;
    private static final Held ENDER = new Held(GooTypes.ENDER, CHRYSM);
    private static final Held CRYSTAL = new Held(GooTypes.CRYSTAL, CHRYSM_CRYSTAL);
    /** A budget that never limits a step, so the step tests read the goo, crystal and knob alone. */
    private static final double UNPACED = Double.MAX_VALUE;
    private static final double TOLERANCE = 0.02;

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
            assertEquals(new Step(GooTypes.ENDER, CHRYSM, CHRYSM_CRYSTAL),
                    CrystallizerPhases.step(ENDER, CRYSTAL, 0, null, ChrysmTier.CHRYSM, UNPACED));
        }

        @Test
        void shortCrystalCrystallizesWhatItPaysFor() {
            assertEquals(new Step(GooTypes.ENDER, 500, 50),
                    CrystallizerPhases.step(ENDER, new Held(GooTypes.CRYSTAL, 50), 0, null, ChrysmTier.CHRYSM, UNPACED));
        }

        @Test
        void noCrystalPauses() {
            assertNull(CrystallizerPhases.step(ENDER, Held.NOTHING, 0, null, ChrysmTier.CHRYSM, UNPACED));
        }

        @Test
        void gooUnderOneStepWaits() {
            assertNull(CrystallizerPhases.step(new Held(GooTypes.ENDER, 9), CRYSTAL, 0, null, ChrysmTier.CHRYSM, UNPACED));
        }

        @Test
        void crystallizingStopsAtTheKnobTier() {
            assertEquals(new Step(GooTypes.ENDER, 400, 40),
                    CrystallizerPhases.step(ENDER, CRYSTAL, CHRYSM - 400, GooTypes.ENDER, ChrysmTier.CHRYSM, UNPACED));
            assertNull(CrystallizerPhases.step(ENDER, CRYSTAL, CHRYSM, GooTypes.ENDER, ChrysmTier.CHRYSM, UNPACED));
        }

        @Test
        void anotherTypeWaitsWhileOneIsCrystallized() {
            assertNull(CrystallizerPhases.step(new Held(GooTypes.ROCK, CHRYSM), CRYSTAL, 500, GooTypes.ENDER,
                    ChrysmTier.BUDDING_CHRYSM, UNPACED));
        }
    }

    @Nested
    class Pace {

        @Test
        void eachTierTakesAboutTwoHundredTicksMore() {
            long crystallized = 0;
            double budget = 0;
            int[] reachedAt = new int[ChrysmTier.values().length];
            int last = reachedAt.length - 1;
            for (int tick = 1; tick <= 1_000 && reachedAt[last] == 0; tick++) {
                budget = CrystallizerPhases.nextBudget(budget, crystallized);
                long steps = (long) (budget / CrystallizerPhases.GOO_PER_CRYSTAL);
                crystallized += steps * CrystallizerPhases.GOO_PER_CRYSTAL;
                budget -= steps * CrystallizerPhases.GOO_PER_CRYSTAL;
                for (ChrysmTier tier : ChrysmTier.values()) {
                    if (reachedAt[tier.ordinal()] == 0 && crystallized >= tier.volume()) {
                        reachedAt[tier.ordinal()] = tick;
                    }
                }
            }
            for (ChrysmTier tier : ChrysmTier.values()) {
                int expected = CrystallizerPhases.TICKS_PER_TIER * (tier.ordinal() + 1);
                assertEquals(expected, reachedAt[tier.ordinal()], expected * TOLERANCE, tier.name());
            }
        }

        @Test
        void thePaceBoundsAStep() {
            assertEquals(new Step(GooTypes.ENDER, 50, 5), CrystallizerPhases.step(ENDER, CRYSTAL, 0, null,
                    ChrysmTier.CHRYSM, 55));
            assertNull(CrystallizerPhases.step(ENDER, CRYSTAL, 0, null, ChrysmTier.CHRYSM, 9));
        }

        @Test
        void anIdleStretchBanksNoBurst() {
            double budget = 0;
            for (int tick = 0; tick < 1_000; tick++) {
                budget = CrystallizerPhases.nextBudget(budget, 0);
            }
            assertTrue(budget <= CrystallizerPhases.paceAllowance(0) + CrystallizerPhases.GOO_PER_CRYSTAL);
        }
    }

    @Test
    void theItemInsideIsTheHighestTierReached() {
        assertNull(CrystallizerPhases.reachedTier(CHRYSM - 1));
        assertEquals(ChrysmTier.CHRYSM, CrystallizerPhases.reachedTier(999_999));
        assertEquals(ChrysmTier.BUDDING_CHRYSM, CrystallizerPhases.reachedTier(1_000_000));
        assertEquals(ChrysmTier.FLOWERING_CHRYSM, CrystallizerPhases.reachedTier(32_000_000));
        assertEquals(ChrysmTier.MATERIA, CrystallizerPhases.reachedTier(1_000_000_000L));
    }

    @Test
    void theKnobWrapsFromThreeToOne() {
        assertEquals(2, CrystallizerPhases.nextKnob(1));
        assertEquals(3, CrystallizerPhases.nextKnob(2));
        assertEquals(1, CrystallizerPhases.nextKnob(3));
    }

    @Test
    void onlyOneCanisterHoldsTheGrowingGoo() {
        CrystallizerPhases.Held ender = new CrystallizerPhases.Held(GooTypes.ENDER, 1000);
        CrystallizerPhases.Held crystal = new CrystallizerPhases.Held(GooTypes.CRYSTAL, 10);
        org.junit.jupiter.api.Assertions.assertFalse(CrystallizerPhases.admits(ender, GooTypes.BLAZE), "blaze beside ender");
        org.junit.jupiter.api.Assertions.assertFalse(CrystallizerPhases.admits(ender, GooTypes.ENDER), "ender beside ender");
        org.junit.jupiter.api.Assertions.assertTrue(CrystallizerPhases.admits(ender, GooTypes.CRYSTAL), "crystal beside ender");
        org.junit.jupiter.api.Assertions.assertTrue(CrystallizerPhases.admits(crystal, GooTypes.ENDER), "ender beside crystal");
        org.junit.jupiter.api.Assertions.assertTrue(CrystallizerPhases.admits(CrystallizerPhases.Held.NOTHING, GooTypes.ENDER),
                "ender beside nothing");
    }
}
