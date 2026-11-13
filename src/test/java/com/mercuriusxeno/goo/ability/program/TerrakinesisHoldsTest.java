package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wind-up the server publishes to the holder's client is the one it runs,
 * so the client shakes every wind-up, even one that starts partway through a
 * hold after a missed lift or a throw, and none while a hunk is held
 * (decision terrakinesis-shakes-the-blocks-before-the-tear).
 */
class TerrakinesisHoldsTest {

    private static final long PRESS = 1000;

    @Test
    void aWindUpAfterAMissAtItsCompletingTickShakesForItsWholeWindUp() {
        UUID player = UUID.randomUUID();
        for (long now = PRESS; now < PRESS + TerrakinesisStep.WINDUP_TICKS; now++) {
            TerrakinesisHolds.windUpTick(player, now);
        }
        // The wind-up completes on its tenth tick; the look struck nothing, so the next tick winds up afresh.
        TerrakinesisHolds.grabbed(player);
        long restart = PRESS + TerrakinesisStep.WINDUP_TICKS;
        TerrakinesisHolds.windUpTick(player, restart);
        TerrakinesisWindUp published = TerrakinesisHolds.windUpOf(player);
        for (long now = restart; now < restart + TerrakinesisStep.WINDUP_TICKS - 1; now++) {
            assertTrue(TerrakinesisStep.windsUp(published.tickAt(now)) && published.tickAt(now) > 0,
                    "tick " + published.tickAt(now) + " of the restarted wind-up should shake");
        }
        assertEquals(TerrakinesisStep.WINDUP_TICKS, published.tickAt(restart + TerrakinesisStep.WINDUP_TICKS - 1));
    }

    @Test
    void aWindUpAfterAThrowsCooldownShakesAndNoneRunsWhileCoolingOrHeld() {
        UUID player = UUID.randomUUID();
        TerrakinesisHolds.windUpTick(player, PRESS);
        TerrakinesisHolds.grabbed(player);
        assertEquals(0, TerrakinesisHolds.windUpOf(player).tickAt(PRESS + 1), "a held hunk shakes nothing");
        long thrown = PRESS + 40;
        TerrakinesisHolds.threw(player, thrown);
        assertEquals(0, TerrakinesisHolds.windUpOf(player).tickAt(thrown + 1), "a cooling throw shakes nothing");
        long ready = thrown + TerrakinesisStep.THROW_COOLDOWN_TICKS;
        assertFalse(TerrakinesisHolds.coolingDown(player, ready));
        TerrakinesisHolds.windUpTick(player, ready);
        assertEquals(1, TerrakinesisHolds.windUpOf(player).tickAt(ready));
        assertEquals(TerrakinesisStep.WINDUP_TICKS - 1,
                TerrakinesisHolds.windUpOf(player).tickAt(ready + TerrakinesisStep.WINDUP_TICKS - 2));
    }

    @Test
    void aThrowHoldsOffTheNextGrabUntilItsCooldownEnds() {
        UUID player = UUID.randomUUID();
        long thrown = PRESS + 40;
        TerrakinesisHolds.threw(player, thrown);
        long ready = thrown + TerrakinesisStep.THROW_COOLDOWN_TICKS;

        assertEquals(List.of(true, false),
                List.of(TerrakinesisHolds.coolingDown(player, ready - 1), TerrakinesisHolds.coolingDown(player, ready)));
    }
}
