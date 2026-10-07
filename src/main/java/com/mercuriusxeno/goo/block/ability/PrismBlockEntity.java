package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The prism's block entity, the anchor its renderer draws the crystal from.
 * decision prism-hosts-the-combos
 */
public class PrismBlockEntity extends GooSyncedBlockEntity {

    /**
     * Creates the prism's block entity.
     *
     * @param pos   the prism's position
     * @param state the prism's block state
     */
    public PrismBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PRISM.get(), pos, state);
    }
}
