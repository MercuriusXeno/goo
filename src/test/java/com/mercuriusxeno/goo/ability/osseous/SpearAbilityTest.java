package com.mercuriusxeno.goo.ability.osseous;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.ThrowRange;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spear, read from the shipped abilities beside Metal's Dart through the ability codec, is Osseous's held arc
 * throw in the SPEAR form, stacking six javelins that strike for less than Dart's and reach farther.
 * decision spear-is-a-barbed-javelin-in-the-dart-family
 */
class SpearAbilityTest {

    private static final AbilityDefinition SPEAR = AbilityJson.decode("osseous_spear");
    private static final AbilityDefinition DART = AbilityJson.decode("metal_dart");
    private static final int SPEAR_STACK = 6;

    @Test
    void spearIsOsseousHeldArcInTheSpearForm() {
        assertEquals(GooTypes.OSSEOUS, SPEAR.gooType());
        assertEquals(AbilityBadge.HELD, SPEAR.badge());
        assertEquals(DeliveryKind.ARC, SPEAR.delivery().kind());
        assertEquals("SPEAR", SPEAR.delivery().form().name());
        assertTrue(SPEAR.delivery().stacksJavelins(), "spear stacks no javelins");
    }

    @Test
    void spearStacksSixJavelins() {
        assertEquals(SPEAR_STACK, SPEAR.delivery().charge().stack());
    }

    @Test
    void spearStrikesForLessThanDart() {
        double spear = damageOf(SPEAR);
        double dart = damageOf(DART);
        assertTrue(spear > 0 && spear < dart, "spear strikes for " + spear + ", dart for " + dart);
    }

    @Test
    void spearReachesPastDart() {
        double spear = ThrowRange.of(SPEAR);
        double dart = ThrowRange.of(DART);
        assertTrue(spear > dart, "spear reaches " + spear + ", dart " + dart);
    }

    private static double damageOf(AbilityDefinition ability) {
        return ability.behaviors().stream().filter(DamageStep.class::isInstance)
                .mapToDouble(step -> ((DamageStep) step).amount().evaluate(Variables.NONE)).sum();
    }
}
