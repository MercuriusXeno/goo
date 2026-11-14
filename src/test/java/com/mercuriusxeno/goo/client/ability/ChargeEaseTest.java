package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast's spheroid eases each step: up from nothing to 2 as the charge
 * starts, then it sits at the size a release would cast and eases into the
 * next size over the quarter second after each step, never ahead of it
 * (decision blast-eases-between-stages-at-a-second-each).
 */
class ChargeEaseTest {

    private static final Charge BLAST = AbilityJson.decode("unstable_blast").delivery().charge();
    private static final float[] PARTIALS = {0f, 0.25f, 0.5f, 0.75f};
    private static final int EASED_HOLD = 105;
    private static final int LONG_HOLD = 120;
    private static final int[] STEPS = {20, 40, 60, 80, 100};
    private static final int TWO_TICKS = 2;
    private static final int QUARTER_SECOND = 5;
    private static final double DELTA = 1e-6;

    @Test
    void theRadiusStartsFromNothingAndNeverShrinks() {
        assertEquals(0, ChargeEase.drawnRadius(BLAST, 0f), DELTA);
        double last = 0;
        for (int tick = 0; tick <= EASED_HOLD; tick++) {
            for (float partial : PARTIALS) {
                double now = ChargeEase.drawnRadius(BLAST, tick + partial);
                assertTrue(now >= last, "the radius shrank at tick " + (tick + partial));
                last = now;
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"5, 2", "20, 2", "25, 3", "40, 3", "45, 4", "60, 4", "65, 5", "80, 5", "85, 6", "100, 6",
            "105, 7"})
    void theRadiusSitsAtEachSizeAndEasesIntoTheNextAQuarterSecondAfterItsStep(int tick, double radius) {
        assertEquals(radius, ChargeEase.drawnRadius(BLAST, tick), DELTA);
    }

    @Test
    void theRadiusNeverRunsAheadOfTheCast() {
        for (int tick = 0; tick <= LONG_HOLD; tick++) {
            for (float partial : PARTIALS) {
                assertTrue(ChargeEase.drawnRadius(BLAST, tick + partial) <= BLAST.stageAt(tick) + DELTA,
                        "the radius ran ahead of the cast at tick " + (tick + partial));
            }
        }
    }

    @Test
    void eachStepEasesRatherThanPops() {
        for (int step : STEPS) {
            double earlier = BLAST.stageAt(step - 1);
            double stage = BLAST.stageAt(step);
            double eased = ChargeEase.drawnRadius(BLAST, step + TWO_TICKS);
            assertTrue(eased > earlier && eased < stage, "two ticks after the step at " + step + " drew " + eased);
            int next = step + Math.round(BLAST.maxTicks() / (float) (BLAST.maxSize() - BLAST.openingSize()));
            for (int tick = step + QUARTER_SECOND; tick < Math.min(next, LONG_HOLD); tick++) {
                assertEquals(stage, ChargeEase.drawnRadius(BLAST, tick), DELTA, "tick " + tick);
            }
        }
    }
}
