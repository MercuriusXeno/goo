package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Scales, read from the shipped abilities through the ability codec, is Durable's, and Crystal holds none.
 * scales-moves-from-crystal-to-durable
 */
class ScalesAbilityTest {

    private static final Identifier DURABLE_SCALES = Identifier.fromNamespaceAndPath("goo", "durable_scales");
    private static final Identifier CRYSTAL_SCALES = Identifier.fromNamespaceAndPath("goo", "crystal_scales");

    @Test
    void scalesIsDurablesAndCrystalHoldsNone() {
        Map<Identifier, AbilityDefinition> shipped = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .collect(Collectors.toMap(AbilityDefinition::id, Function.identity()));

        assertFalse(shipped.containsKey(CRYSTAL_SCALES), "crystal_scales still ships");
        assertEquals(List.of(GooTypes.DURABLE), shipped.values().stream()
                .filter(ability -> ability.id().equals(DURABLE_SCALES))
                .map(AbilityDefinition::gooType)
                .toList());
    }
}
