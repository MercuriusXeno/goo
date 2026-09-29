package com.mercuriusxeno.goo.block.crystallizer;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Held;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Roles;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerPhases.Step;
import com.mercuriusxeno.goo.item.ChrysmTier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        void eachTierCrystallizesAtItsOwnEvenRate() {
            assertEquals(64, CrystallizerPhases.paceAllowance(0), 1e-9);
            assertEquals(64, CrystallizerPhases.paceAllowance(31_990), 1e-9);
            assertEquals(968, CrystallizerPhases.paceAllowance(32_000), 1e-9);
            assertEquals(968, CrystallizerPhases.paceAllowance(999_990), 1e-9);
            assertEquals(15_500, CrystallizerPhases.paceAllowance(1_000_000), 1e-9);
            assertEquals(15_500, CrystallizerPhases.paceAllowance(20_000_000), 1e-9);
            assertEquals(242_000, CrystallizerPhases.paceAllowance(32_000_000), 1e-9);
            assertEquals(242_000, CrystallizerPhases.paceAllowance(999_999_990), 1e-9);
        }

        @Test
        void fromEmptyEachTierCompletesAtItsDoublingTickSpendingATenthInCrystal() {
            Held endless = new Held(GooTypes.ENDER, Integer.MAX_VALUE);
            Held endlessCrystal = new Held(GooTypes.CRYSTAL, Integer.MAX_VALUE);
            long crystallized = 0;
            long crystalSpent = 0;
            double budget = 0;
            int[] reachedAt = new int[ChrysmTier.values().length];
            long[] crystalAt = new long[ChrysmTier.values().length];
            for (int tick = 1; tick <= 8_000; tick++) {
                budget = CrystallizerPhases.nextBudget(budget, crystallized);
                Step step = CrystallizerPhases.step(endless, endlessCrystal, crystallized, GooTypes.ENDER,
                        ChrysmTier.MATERIA, budget);
                if (step != null) {
                    crystallized += step.goo();
                    crystalSpent += step.crystal();
                    budget -= step.goo();
                }
                for (ChrysmTier tier : ChrysmTier.values()) {
                    if (reachedAt[tier.ordinal()] == 0 && crystallized >= tier.volume()) {
                        reachedAt[tier.ordinal()] = tick;
                        crystalAt[tier.ordinal()] = crystalSpent;
                    }
                }
            }
            int[] expected = {500, 1_500, 3_500, 7_500};
            for (ChrysmTier tier : ChrysmTier.values()) {
                assertEquals(expected[tier.ordinal()], reachedAt[tier.ordinal()], tier.name());
                assertEquals(tier.volume() / CrystallizerPhases.GOO_PER_CRYSTAL, crystalAt[tier.ordinal()],
                        tier.name() + " crystal spent");
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

    @Nested
    class Knob {

        @Test
        void aClickStepsUpThroughFivePositionsAndWrapsMateriaToOff() {
            assertEquals(1, CrystallizerPhases.nextKnob(0));
            assertEquals(2, CrystallizerPhases.nextKnob(1));
            assertEquals(3, CrystallizerPhases.nextKnob(2));
            assertEquals(4, CrystallizerPhases.nextKnob(3));
            assertEquals(0, CrystallizerPhases.nextKnob(4));
        }

        @Test
        void offCapsNothingAndOneToFourCapAtEachTier() {
            assertNull(CrystallizerPhases.tierForKnob(0));
            assertEquals(ChrysmTier.CHRYSM, CrystallizerPhases.tierForKnob(1));
            assertEquals(ChrysmTier.BUDDING_CHRYSM, CrystallizerPhases.tierForKnob(2));
            assertEquals(ChrysmTier.FLOWERING_CHRYSM, CrystallizerPhases.tierForKnob(3));
            assertEquals(ChrysmTier.MATERIA, CrystallizerPhases.tierForKnob(4));
        }

        @Test
        void offCrystallizesNothing() {
            assertNull(CrystallizerPhases.step(ENDER, CRYSTAL, 0, null, null, UNPACED));
            assertNull(CrystallizerPhases.step(ENDER, CRYSTAL, 500, GooTypes.ENDER, null, UNPACED));
        }

        @Test
        void offLeavesAFormedCrystalMatureAndNothingElse() {
            assertTrue(CrystallizerPhases.isMature(CHRYSM, null));
            assertFalse(CrystallizerPhases.isMature(CHRYSM - 10, null));
            assertFalse(CrystallizerPhases.isMature(CHRYSM, ChrysmTier.BUDDING_CHRYSM));
            assertTrue(CrystallizerPhases.isMature(ChrysmTier.BUDDING_CHRYSM.volume(), ChrysmTier.BUDDING_CHRYSM));
        }
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
