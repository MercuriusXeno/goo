package com.mercuriusxeno.goo.ability.petrify;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A petrify gauge fills toward full, holds while the fog lingers, then drains
 * back slowly to nothing; its share is what slows the mob and spreads its
 * stone (decision petrify-stone-encasement-and-calcify-map).
 */
class PetrificationTest {

    private static final float DELTA = 1e-6f;
    private static final long NOW = 1_000L;

    @Test
    void fillsAccumulateAndCapAtFull() {
        Petrification half = Petrification.NONE.fill(40f, NOW).fill(10f, NOW + 1);
        assertEquals(50f, half.gauge(), DELTA);
        assertEquals(0.5f, half.share(), DELTA);
        assertFalse(half.full());
        assertTrue(Petrification.NONE.fill(90f, NOW).fill(30f, NOW).full());
    }

    @Test
    void theGaugeHoldsWhileTheFogLingers() {
        Petrification filled = Petrification.NONE.fill(50f, NOW);
        assertSame(filled, filled.drained(NOW + Petrification.DRAIN_DELAY_TICKS));
    }

    @Test
    void onceTheFogLeavesTheGaugeDrainsSlowly() {
        Petrification filled = Petrification.NONE.fill(50f, NOW);
        Petrification drained = filled.drained(NOW + Petrification.DRAIN_DELAY_TICKS + 1);
        assertEquals(50f - Petrification.DRAIN_PER_TICK, drained.gauge(), DELTA);
    }

    @Test
    void aGaugeDrainedEmptyIsNone() {
        Petrification nearly = Petrification.NONE.fill(Petrification.DRAIN_PER_TICK / 2, NOW);
        assertSame(Petrification.NONE, nearly.drained(NOW + Petrification.DRAIN_DELAY_TICKS + 1));
        assertFalse(Petrification.NONE.started());
    }

    /**
     * Heal's stream lowers a gauge by its amount and the gauge keeps the time
     * the fog last filled it (decision heal-stream-drains-the-petrify-gauge).
     */
    @Nested
    class DrainedBy {

        @Test
        void lowersTheGaugeByTheAmount() {
            Petrification filled = Petrification.NONE.fill(50f, NOW);
            assertEquals(42f, filled.drainedBy(8f).gauge(), DELTA);
        }

        @Test
        void keepsTheTimeTheFogLastFilledIt() {
            Petrification filled = Petrification.NONE.fill(50f, NOW);
            assertEquals(NOW, filled.drainedBy(8f).lastFill());
        }

        @Test
        void aGaugeDrainedPastEmptyIsNone() {
            Petrification filled = Petrification.NONE.fill(5f, NOW);
            assertSame(Petrification.NONE, filled.drainedBy(5f));
            assertSame(Petrification.NONE, filled.drainedBy(8f));
        }
    }
}
