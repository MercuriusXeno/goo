package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.GrabStep;
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

    private static final double SHARED_THROW_RANGE = 64;

    private static ClientAbility ability(AbilityBadge badge, List<String> tags) {
        return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "aimed"), "ability.goo.aimed", "", 0,
                tags, List.of(), 0, Delivery.ARC, badge, List.of());
    }

    private static ClientAbility shipped(String name) {
        AbilityDefinition definition = AbilityJson.decode(name);
        return new ClientAbility(definition.id(), definition.displayName(), "", 0, List.of(), definition.behaviors(),
                0, definition.delivery(), definition.badge(), List.of());
    }

    /**
     * The glove aims as far as the selected ability's delivery names.
     * decision each-ability-names-its-range-and-the-shared-throw-range-retires
     */
    @Test
    void anAbilityWhoseDeliveryNamesSixteenBlocksAimsSixteenBlocks() {
        assertEquals(16, GloveAim.aimRangeOf(shipped("weird_mitosis"), null, SHARED_THROW_RANGE));
    }

    @Test
    void grabAimsAsFarAsItsReach() {
        ClientAbility grab = shipped("kinetic_grab");

        assertEquals(GrabStep.reachOf(grab.behaviors(), null).orElseThrow(),
                GloveAim.aimRangeOf(grab, null, SHARED_THROW_RANGE));
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
