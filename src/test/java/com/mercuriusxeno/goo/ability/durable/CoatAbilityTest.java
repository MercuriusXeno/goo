package com.mercuriusxeno.goo.ability.durable;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Durable's residual, read from the shipped abilities through the ability codec, is Coat.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 */
class CoatAbilityTest {

    @Test
    void durablesOneResidualIsCoat() {
        List<Identifier> residuals = AbilityJson.files().stream()
                .map(AbilityJson::decode)
                .filter(ability -> ability.gooType().equals(GooTypes.DURABLE))
                .filter(ability -> ability.badge() == AbilityBadge.RESIDUAL)
                .map(AbilityDefinition::id)
                .toList();

        assertEquals(List.of(Identifier.fromNamespaceAndPath("goo", "durable_coat")), residuals);
    }
}
