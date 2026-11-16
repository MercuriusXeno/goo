package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.HeldRoute;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped gate ability runs on every tick right click is held, so holding pulls the moon in and letting go
 * stops the pull.
 * decision holding-right-click-pulls-the-moon-and-early-release-cancels
 */
class AstralGateAbilityTest {

    private final AbilityDefinition gate = AbilityJson.decode("astral_gate");

    @Test
    void gateRunsWhileRightClickIsHeld() {
        assertTrue(HeldRoute.runsWhileHeld(gate.delivery(), gate.badge()),
                "the gate wears " + gate.badge() + " on " + gate.delivery() + ", which runs once rather than held");
    }
}
