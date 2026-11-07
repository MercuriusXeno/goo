package com.mercuriusxeno.goo.block.outsider;

import com.mercuriusxeno.goo.registry.GooBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block entity under each voidstone, holding nothing: it is how the
 * client finds the voidstone faces its star shader draws.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class VoidstoneBlockEntity extends BlockEntity {

    /**
     * @param pos   the block's position
     * @param state the block's state
     */
    public VoidstoneBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.VOIDSTONE.get(), pos, state);
    }
}
