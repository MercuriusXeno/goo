package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Covers Drain's leech: the caster heals the fraction of the health the
 * strike took, and nothing where the strike took none; a leech written
 * without on-leech steps runs none.
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

    // drain-plays-a-steady-draining-loop-at-your-ears: every other leech decodes as it did, running no accent
    @Test
    void aLeechWrittenWithoutOnLeechStepsRunsNone() {
        Step decoded = StepTypes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\": \"leech\", \"fraction\": 0.5, \"strike\": [{\"type\": \"damage\", \"amount\": 2}]}"))
                .getOrThrow();
        assertEquals(0, assertInstanceOf(LeechStep.class, decoded).onLeech().size());
    }
}
