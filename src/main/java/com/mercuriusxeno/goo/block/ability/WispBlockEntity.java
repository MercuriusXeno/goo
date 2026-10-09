package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The wisp's block entity, which holds nothing: it stands so the wisp's
 * renderer can draw the floating mote.
 * decision radiant-wisps-where-light-is-low
 */
public class WispBlockEntity extends BlockEntity {

    /**
     * Creates the wisp's block entity.
     *
     * @param pos   the wisp's position
     * @param state the wisp's block state
     */
    public WispBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.WISP.get(), pos, state);
    }
}
