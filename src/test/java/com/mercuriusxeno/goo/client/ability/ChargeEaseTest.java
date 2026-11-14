package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast's spheroid draws at a radius that grows continuously: up from
 * nothing as the charge starts, easing from each stage's size to the next,
 * and at the stage's whole size as each stage completes
 * (decision blast-eases-between-stages-at-a-second-each).
 */
class ChargeEaseTest {

    private static final Charge BLAST = AbilityJson.decode("unstable_blast").delivery().charge();
    private static final int FULL_CHARGE = 100;
    private static final int STAGE_TICKS = 20;
    private static final float[] PARTIALS = {0f, 0.25f, 0.5f, 0.75f};
    private static final double QUARTER_BLOCK = 0.25;
    private static final double DELTA = 1e-6;

    @Test
    void theRadiusStartsFromNothing() {
        assertEquals(0, ChargeEase.drawnRadius(BLAST, 0f), DELTA);
    }

    @Test
    void theRadiusNeverShrinksNorJumpsAQuarterBlock() {
        double last = ChargeEase.drawnRadius(BLAST, 0f);
        for (int tick = 0; tick <= FULL_CHARGE; tick++) {
            for (float partial : PARTIALS) {
                double now = ChargeEase.drawnRadius(BLAST, tick + partial);
                assertTrue(now >= last, "the radius shrank at tick " + (tick + partial));
                last = now;
            }
            double step = ChargeEase.drawnRadius(BLAST, tick + 1f) - ChargeEase.drawnRadius(BLAST, tick);
            assertTrue(step < QUARTER_BLOCK, "the radius moved " + step + " blocks at tick " + tick);
        }
    }

    @Test
    void theRadiusIsTheWholeStageAsEachStageCompletes() {
        for (int stage = 1; stage * STAGE_TICKS <= FULL_CHARGE; stage++) {
            int tick = stage * STAGE_TICKS;
            assertEquals(BLAST.stageAt(tick), ChargeEase.drawnRadius(BLAST, tick), DELTA, "tick " + tick);
        }
    }
}
