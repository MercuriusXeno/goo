package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers Drain's leech: the caster heals the fraction of the health the
 * strike took, and nothing where the strike took none.
 */
class LeechStepTest {

    private static final float FRACTION = 0.5f;
    private static final float TOLERANCE = 1e-6f;

    @Test
    void theCasterHealsTheFractionOfTheHealthTaken() {
        assertEquals(1f, LeechStep.healOf(20f, 18f, FRACTION), TOLERANCE);
    }

    @Test
    void aStrikeTakingNothingHealsNothing() {
        assertEquals(0f, LeechStep.healOf(20f, 20f, FRACTION), TOLERANCE);
    }

    @Test
    void aTargetHealedBetweenReadsHealsNothing() {
        assertEquals(0f, LeechStep.healOf(18f, 20f, FRACTION), TOLERANCE);
    }
}
