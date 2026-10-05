package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * The state a chain marker carries through a fall, taken when its support
 * breaks and restored where it lands, ability id included so the landed
 * marker runs the same ability's program (decision no-throw-without-ability).
 *
 * @param gooType    the marker's goo type
 * @param abilityId  the ability the marker runs at fuse expiry
 * @param stackCount the goo stacked on the marker
 * @param maxStacks  the stack ceiling
 * @param fuse       the fuse ticks remaining
 * @param face       the face the marker was placed on
 */
public record ChainMarkerSnapshot(ResourceKey<GooTypeDefinition> gooType, String abilityId,
                                  int stackCount, int maxStacks, int fuse, Direction face) {

    /**
     * Takes the snapshot of a standing marker.
     *
     * @param be the marker's block entity
     * @return the marker's state
     */
    public static ChainMarkerSnapshot of(ChainMarkerBlockEntity be) {
        return new ChainMarkerSnapshot(be.getGooType(), be.getAbilityId(), be.getStackCount(),
                be.getMaxStacks(), be.getFuseRemaining(), be.getPlacedFace());
    }
}
