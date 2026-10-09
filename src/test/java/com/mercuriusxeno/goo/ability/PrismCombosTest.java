package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Which program a goo landing on a prism runs: the landing ability's own
 * on_prism reaction first, its type's prism ability otherwise, and neither
 * for a type with no combo (decision prism-hosts-the-combos).
 */
class PrismCombosTest {

    private static final List<Step> BEHAVIORS = List.of(mock(Step.class));
    private static final List<Step> REACTION = List.of(mock(Step.class));

    private static AbilityDefinition ability(String name, AbilityBadge badge, List<Step> onPrism) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.GLOW, name, "", 0, 0,
                Delivery.ARC, BEHAVIORS, List.of(), badge, List.of(), AbilityArea.NONE, IndicatorShowing.HELD,
                List.of(), onPrism);
    }

    @Test
    void anOnPrismReactionRunsInPlaceOfTheTypesPrismAbility() {
        AbilityDefinition bulb = ability("glow_crystal", AbilityBadge.WORLD, REACTION);
        AbilityDefinition prism = ability("glow_prism", AbilityBadge.PRISM, List.of());

        AbilityDefinition source = PrismCombos.comboSource(bulb, prism);

        assertSame(bulb, source);
        assertEquals(REACTION, PrismCombos.comboSteps(source));
    }

    @Test
    void anAbilityWithoutAReactionRunsTheTypesPrismAbilitysBehaviors() {
        AbilityDefinition laser = ability("glow_laser", AbilityBadge.WORLD, List.of());
        AbilityDefinition prism = ability("glow_prism", AbilityBadge.PRISM, REACTION);

        AbilityDefinition source = PrismCombos.comboSource(laser, prism);

        assertSame(prism, source);
        assertEquals(BEHAVIORS, PrismCombos.comboSteps(source));
    }

    @Test
    void aPrismAbilityThrownAtAPrismIsItsOwnCombo() {
        AbilityDefinition relay = ability("pulse_relay", AbilityBadge.PRISM, List.of());
        AbilityDefinition metronome = ability("pulse_metronome", AbilityBadge.PRISM, List.of());

        assertSame(relay, PrismCombos.comboSource(relay, metronome));
    }

    @Test
    void aTypeWithNeitherHasNoCombo() {
        assertNull(PrismCombos.comboSource(ability("glow_laser", AbilityBadge.WORLD, List.of()), null));
    }
}
