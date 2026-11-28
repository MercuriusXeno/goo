package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.BladeStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blade, read from the shipped abilities, is Osseous's held self ability and forms a bone shotel only once right
 * click is held through its form time; BladeTests drives the latch, the refused cast and the dissolving click.
 * decision blade-forms-a-bone-shotel-in-hand
 */
class BoneBladesTest {

    private static final Identifier OSSEOUS_BLADE = Identifier.fromNamespaceAndPath("goo", "osseous_blade");
    private static AbilityDefinition blade;

    @BeforeAll
    static void decodeTheShippedBlade() {
        blade = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.id().equals(OSSEOUS_BLADE)).findFirst().orElse(null);
        assertNotNull(blade, "osseous_blade does not ship");
    }

    @Test
    void bladeIsOsseousHeldFormingOnTheHoldOfRightClick() {
        assertEquals(GooTypes.OSSEOUS, blade.gooType());
        assertEquals(AbilityBadge.HELD, blade.badge());
        assertEquals(DeliveryKind.SELF, blade.delivery().kind());
        assertTrue(blade.delivery().charges(), "Blade's hold forms nothing over time");
        assertTrue(BladeStep.in(blade.behaviors()).isPresent(), "Blade's program lays no blade");
    }

    @Test
    void theBladeFormsOnceTheHoldReachesItsFormTime() {
        int formTicks = blade.delivery().chargeTicks();
        assertTrue(BoneBlades.formsAt(blade.delivery().chargeShare(formTicks)), "a full hold forms no blade");
        assertFalse(BoneBlades.formsAt(blade.delivery().chargeShare(formTicks - 1)), "a short hold forms a blade");
    }
}
