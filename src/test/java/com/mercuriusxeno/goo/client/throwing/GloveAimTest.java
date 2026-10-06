package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The glove's aim mode reads the selected ability's badge, never its entity
 * tag (decision target-kind-configured-per-ability).
 */
class GloveAimTest {

    private static ClientAbility ability(AbilityBadge badge, List<String> tags) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "aimed"), "ability.goo.aimed", "", 0,
                tags, List.of(), 0, Delivery.ARC, badge, List.of());
    }

    @Test
    void aMobBadgeFavorsEntitiesWithTheEntityTagAbsent() {
        assertEquals(TargetingHint.ENTITY, GloveAim.hintOf(ability(AbilityBadge.MOB, List.of())));
    }

    @Test
    void theEntityTagNoLongerSteersAWorldOrFreeAbility() {
        assertEquals(TargetingHint.BLOCK, GloveAim.hintOf(ability(AbilityBadge.WORLD, List.of(AbilityTags.ENTITY))));
        assertEquals(TargetingHint.POINT, GloveAim.hintOf(ability(AbilityBadge.FREE, List.of(AbilityTags.ENTITY))));
    }
}
