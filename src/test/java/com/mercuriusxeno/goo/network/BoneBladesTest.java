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
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blade, read from the shipped abilities, forms a bone shotel once right click is held through its form time: the
 * shotel latches in the casting hand, the hand casts nothing else while it stands, and the next right click
 * dissolves it.
 * decision blade-forms-a-bone-shotel-in-hand
 */
class BoneBladesTest {

    private static final Identifier OSSEOUS_BLADE = Identifier.fromNamespaceAndPath("goo", "osseous_blade");
    private static final UUID CASTER = new UUID(7L, 11L);
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
    void aHoldThroughTheFormTimeLatchesTheBladeUntilTheNextRightClick() {
        BoneBlades blades = new BoneBlades();
        int formTicks = blade.delivery().chargeTicks();
        assertTrue(BoneBlades.formsAt(blade.delivery().chargeShare(formTicks)));
        blades.latch(CASTER, BoneBlades.Blade.of(BladeStep.in(blade.behaviors()).orElseThrow(), true));

        assertTrue(blades.bladeOf(CASTER).isPresent(), "the blade did not latch");
        assertTrue(blades.refusesCast(CASTER), "the blade's hand casts another ability while it stands");

        assertTrue(blades.dissolve(CASTER), "the next right click found no blade to dissolve");
        assertTrue(blades.bladeOf(CASTER).isEmpty(), "the blade stands after the next right click");
        assertFalse(blades.refusesCast(CASTER), "the hand stays refused once the blade is gone");
    }

    @Test
    void aHoldShortOfTheFormTimeFormsNoBlade() {
        int formTicks = blade.delivery().chargeTicks();
        assertFalse(BoneBlades.formsAt(blade.delivery().chargeShare(formTicks - 1)));
    }
}
