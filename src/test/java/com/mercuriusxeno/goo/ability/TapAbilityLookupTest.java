package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A tap's drip runs the type's tap-tagged ability lowest by order, and a
 * type carrying no tap-tagged ability answers none.
 */
class TapAbilityLookupTest {

    static AbilityDefinition ability(String name, int order, String... tags) {
        return new AbilityDefinition(Identifier.fromNamespaceAndPath("goo", name), GooTypes.ROCK,
                name, "", order, null, AbilityDefinition.ChainConfig.DEFAULT, List.of(), List.of(tags));
    }

    @Test
    void lowestOrderTapAbilityIsPicked() {
        AbilityDefinition entity = ability("rock_throw", 0, AbilityTags.ENTITY);
        AbilityDefinition tapSecond = ability("rock_tap_second", 2, AbilityTags.TAP);
        AbilityDefinition tapFirst = ability("rock_tap_first", 1, AbilityTags.TAP);

        assertSame(tapFirst, AbilityRegistry.firstTagged(List.of(entity, tapSecond, tapFirst), AbilityTags.TAP));
    }

    @Test
    void typeWithNoTapAbilityAnswersNone() {
        AbilityDefinition entity = ability("rock_throw", 0, AbilityTags.ENTITY);

        assertNull(AbilityRegistry.firstTagged(List.of(entity), AbilityTags.TAP));
        assertNull(AbilityRegistry.firstTagged(List.of(), AbilityTags.TAP));
    }
}
