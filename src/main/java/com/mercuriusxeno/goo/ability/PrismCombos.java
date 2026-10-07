package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.Step;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Which program a goo landing on a prism runs: the landing ability's own
 * {@code on_prism} reaction where it carries one, so a world ability such as
 * glow's Bulb reacts to a prism by itself, and otherwise its type's
 * prism-badged ability. A type with neither has no combo.
 * decision prism-hosts-the-combos
 */
public final class PrismCombos {

    private PrismCombos() {
    }

    /**
     * The ability whose program is the combo a landing runs on a prism.
     *
     * @param landing   the ability that landed on the prism
     * @param typePrism the landing type's prism ability, null when the type has none
     * @return the landing ability when it reacts to a prism itself, else the type's prism ability, or null for neither
     */
    public static @Nullable AbilityDefinition comboSource(AbilityDefinition landing,
                                                         @Nullable AbilityDefinition typePrism) {
        return reactsToPrisms(landing) ? landing : typePrism;
    }

    /**
     * The program a combo source runs on the prism: a prism ability's own
     * behaviors, or another ability's {@code on_prism} reaction.
     *
     * @param source the combo's ability
     * @return the combo's program
     */
    public static List<Step> comboSteps(AbilityDefinition source) {
        return source.badge() == AbilityBadge.PRISM ? source.behaviors() : source.onPrism();
    }

    private static boolean reactsToPrisms(AbilityDefinition ability) {
        return ability.badge() != AbilityBadge.PRISM && !ability.onPrism().isEmpty();
    }
}
