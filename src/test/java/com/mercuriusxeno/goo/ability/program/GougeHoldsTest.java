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
class GougeHoldsTest {

    private static final long PRESS = 1000;

    @Test
    void aWindUpAfterAMissAtItsCompletingTickShakesForItsWholeWindUp() {
        UUID player = UUID.randomUUID();
        for (long now = PRESS; now < PRESS + GougeStep.WINDUP_TICKS; now++) {
            GougeHolds.windUpTick(player, now);
        }
        // The wind-up completes on its tenth tick; the look struck nothing, so the next tick winds up afresh.
        GougeHolds.grabbed(player);
        long restart = PRESS + GougeStep.WINDUP_TICKS;
        GougeHolds.windUpTick(player, restart);
        GougeWindUp published = GougeHolds.windUpOf(player);
        for (long now = restart; now < restart + GougeStep.WINDUP_TICKS - 1; now++) {
            assertTrue(GougeStep.windsUp(published.tickAt(now)) && published.tickAt(now) > 0,
                    "tick " + published.tickAt(now) + " of the restarted wind-up should shake");
        }
        assertEquals(GougeStep.WINDUP_TICKS, published.tickAt(restart + GougeStep.WINDUP_TICKS - 1));
    }

    @Test
    void aWindUpAfterAThrowsCooldownShakesAndNoneRunsWhileCoolingOrHeld() {
        UUID player = UUID.randomUUID();
        GougeHolds.windUpTick(player, PRESS);
        GougeHolds.grabbed(player);
        assertEquals(0, GougeHolds.windUpOf(player).tickAt(PRESS + 1), "a held hunk shakes nothing");
        long thrown = PRESS + 40;
        GougeHolds.threw(player, thrown);
        assertEquals(0, GougeHolds.windUpOf(player).tickAt(thrown + 1), "a cooling throw shakes nothing");
        long ready = thrown + GougeStep.THROW_COOLDOWN_TICKS;
        assertFalse(GougeHolds.coolingDown(player, ready));
        GougeHolds.windUpTick(player, ready);
        assertEquals(1, GougeHolds.windUpOf(player).tickAt(ready));
        assertEquals(GougeStep.WINDUP_TICKS - 1,
                GougeHolds.windUpOf(player).tickAt(ready + GougeStep.WINDUP_TICKS - 2));
    }

    @Test
    void aThrowHoldsOffTheNextGrabUntilItsCooldownEnds() {
        UUID player = UUID.randomUUID();
        long thrown = PRESS + 40;
        GougeHolds.threw(player, thrown);
        long ready = thrown + GougeStep.THROW_COOLDOWN_TICKS;

        assertEquals(List.of(true, false),
                List.of(GougeHolds.coolingDown(player, ready - 1), GougeHolds.coolingDown(player, ready)));
    }
}
