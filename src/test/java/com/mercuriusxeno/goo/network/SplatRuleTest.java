package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A blob splats goo on the mob it strikes unless its ability is tagged
 * no_splat, as Crush is, whose program draws the hit as rubble instead
 * (decision crush-blob-breaks-along-its-strike).
 */
class SplatRuleTest {

    private static final Identifier MADE_UP = Identifier.fromNamespaceAndPath("goo", "made_up_strike");
    private static final int A_COST = 1000;

    private static AbilityDefinition tagged(List<String> tags) {
        return new AbilityDefinition(MADE_UP, GooTypes.ROCK, MADE_UP.toString(), "", 0, A_COST, Delivery.ARC,
                List.of(), tags, AbilityBadge.FREE, List.of());
    }

    @Test
    void anAbilityTaggedNoSplatDrawsNoSplat() {
        assertFalse(GooEffectScheduler.splats(tagged(List.of(AbilityTags.ENTITY, AbilityTags.NO_SPLAT))));
    }

    @Test
    void anyOtherAbilityAndNoAbilitySplat() {
        assertTrue(GooEffectScheduler.splats(tagged(List.of(AbilityTags.ENTITY))));
        assertTrue(GooEffectScheduler.splats(null));
    }
}
