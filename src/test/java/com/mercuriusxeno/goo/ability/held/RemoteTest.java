package com.mercuriusxeno.goo.ability.held;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mercuriusxeno.goo.Goo;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

/** Remote lays its reach as an ADD_VALUE modifier on the player's block and entity interaction ranges. */
class RemoteTest {

    private static final double REACH = 8;
    private static final Identifier BLOCK_REACH = Identifier.fromNamespaceAndPath(Goo.MODID, "remote_block_reach");
    private static final Identifier ENTITY_REACH = Identifier.fromNamespaceAndPath(Goo.MODID, "remote_entity_reach");

    private final AttributeInstance blockRange = new AttributeInstance(Attributes.BLOCK_INTERACTION_RANGE, range -> { });
    private final AttributeInstance entityRange = new AttributeInstance(Attributes.ENTITY_INTERACTION_RANGE, range -> { });

    private void layOnAPlayer() {
        LivingEntity player = mock(LivingEntity.class);
        when(player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE)).thenReturn(blockRange);
        when(player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE)).thenReturn(entityRange);
        Remote.lay(player, REACH);
    }

    // decision telekinesis-enacts-at-extended-reach
    @Test
    void theBlockInteractionRangeGainsTheReachAsAnAddedValue() {
        layOnAPlayer();

        assertEquals(Set.of(new AttributeModifier(BLOCK_REACH, REACH, AttributeModifier.Operation.ADD_VALUE)),
                blockRange.getModifiers());
    }

    // decision telekinesis-enacts-at-extended-reach
    @Test
    void theEntityInteractionRangeGainsTheReachAsAnAddedValue() {
        layOnAPlayer();

        assertEquals(Set.of(new AttributeModifier(ENTITY_REACH, REACH, AttributeModifier.Operation.ADD_VALUE)),
                entityRange.getModifiers());
    }
}
