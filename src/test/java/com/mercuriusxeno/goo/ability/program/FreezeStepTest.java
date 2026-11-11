package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The frost a freeze step lays, read from the shipped ability JSON.
 */
class FreezeStepTest {

    /** Cold's freeze each held tick, in health points: past a full Nova's 16 over its 30 held ticks. */
    private static final double COLD_PER_TICK = 0.8;
    private static final double DELTA = 1e-9;

    // decision frozen-gauge-per-mob-encases-when-full
    @Test
    void coldsWindFreezesEightTenthsOfAHealthPointEachTick() {
        FreezeStep freeze = AbilityJson.decode("frost_cold").behaviors().stream()
                .filter(FreezeStep.class::isInstance).map(FreezeStep.class::cast).findFirst().orElseThrow();
        assertEquals(COLD_PER_TICK, freeze.amount().evaluate(Variables.NONE), DELTA);
    }
}
