package com.mercuriusxeno.goo.ability.frost;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frozen gauge's math: a hit's share scales down by max health, hits
 * stack by adding, a full gauge holds before it thaws, and a physical hit's
 * multiplier rises with the gauge.
 */
class FrozenTest {

    private static final float EPSILON = 1e-6f;
    private static final FrostCurve HELD_THREE_HUNDRED_TICKS = new FrostCurve(300, 0.005f, 0.5f);
    private static final long NOW = 1000L;

    @Nested
    class Scaling {

        @Test
        void aHitFreezesItsAmountOverMaxHealth() {
            assertEquals(0.5f, Frozen.shareOf(10f, 20f), EPSILON);
            assertEquals(0.1f, Frozen.shareOf(10f, 100f), EPSILON);
        }

        @Test
        void aMobWithNoHealthTakesNoShare() {
            assertEquals(0f, Frozen.shareOf(10f, 0f));
        }
    }

    @Nested
    class Stacking {

        @Test
        void hitsAddWithNoDiminishing() {
            Frozen twice = Frozen.NONE.add(0.3f, HELD_THREE_HUNDRED_TICKS, NOW).add(0.3f, HELD_THREE_HUNDRED_TICKS, NOW + 1);
            assertEquals(0.6f, twice.gauge(), EPSILON);
            assertFalse(twice.full());
        }

        @Test
        void theGaugeCapsAtFull() {
            Frozen over = Frozen.NONE.add(0.7f, HELD_THREE_HUNDRED_TICKS, NOW).add(0.7f, HELD_THREE_HUNDRED_TICKS, NOW);
            assertEquals(Frozen.FULL, over.gauge());
            assertTrue(over.full());
        }

        @Test
        void aHitShortOfFullSetsNoHold() {
            assertEquals(0L, Frozen.NONE.add(0.5f, HELD_THREE_HUNDRED_TICKS, NOW).holdUntil());
        }

        @Test
        void aHitThatFillsTheGaugeHoldsItTheCurvesHold() {
            assertEquals(NOW + HELD_THREE_HUNDRED_TICKS.holdTicks(), Frozen.NONE.add(1f, HELD_THREE_HUNDRED_TICKS, NOW).holdUntil());
        }
    }

    @Nested
    class Thawing {

        @Test
        void aFullGaugeHoldsUntilItsHoldRunsOut() {
            Frozen full = Frozen.NONE.add(1f, HELD_THREE_HUNDRED_TICKS, NOW);
            assertSame(full, full.thawed(NOW + HELD_THREE_HUNDRED_TICKS.holdTicks() - 1));
        }

        @Test
        void aFullGaugeThawsBelowFullOnceTheHoldRunsOut() {
            Frozen thawing = Frozen.NONE.add(1f, HELD_THREE_HUNDRED_TICKS, NOW).thawed(NOW + HELD_THREE_HUNDRED_TICKS.holdTicks());
            assertEquals(Frozen.FULL - HELD_THREE_HUNDRED_TICKS.thawPerTick(), thawing.gauge(), EPSILON);
            assertFalse(thawing.full());
        }

        @Test
        void aPartGaugeThawsByTheRateEachTick() {
            Frozen part = Frozen.NONE.add(0.5f, HELD_THREE_HUNDRED_TICKS, NOW);
            assertEquals(0.5f - HELD_THREE_HUNDRED_TICKS.thawPerTick(), part.thawed(NOW + 1).gauge(), EPSILON);
        }

        @Test
        void aGaugeThawedPastEmptyIsNone() {
            Frozen trace = Frozen.NONE.add(HELD_THREE_HUNDRED_TICKS.thawPerTick() / 2, HELD_THREE_HUNDRED_TICKS, NOW);
            assertSame(Frozen.NONE, trace.thawed(NOW + 1));
        }
    }

    @Nested
    class Vulnerability {

        @Test
        void anEmptyGaugeLeavesAPhysicalHitWhole() {
            assertEquals(1f, Frozen.NONE.physicalDamageMultiplier());
        }

        @Test
        void theMultiplierRisesInProportionToTheGauge() {
            assertEquals(1.25f, Frozen.NONE.add(0.5f, HELD_THREE_HUNDRED_TICKS, NOW).physicalDamageMultiplier(), EPSILON);
            assertEquals(1.5f, Frozen.NONE.add(1f, HELD_THREE_HUNDRED_TICKS, NOW).physicalDamageMultiplier(), EPSILON);
        }
    }
}
