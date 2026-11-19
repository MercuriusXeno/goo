package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Grudge's damage seam: the ring deals the health its caster is missing,
 * maximum health less current health, and nothing at full health.
 */
class GrudgeStepTest {

    private static final float MAX_HEALTH = 20f;

    @Test
    void aWoundedCasterDealsTheHealthTheyAreMissing() {
        assertEquals(14f, GrudgeStep.missingHealthDamage(MAX_HEALTH, 6f));
    }

    @Test
    void aCasterAtFullHealthDealsNothing() {
        assertEquals(0f, GrudgeStep.missingHealthDamage(MAX_HEALTH, MAX_HEALTH));
    }
}
