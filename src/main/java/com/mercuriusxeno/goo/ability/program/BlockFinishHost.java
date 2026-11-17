package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a heated block finishes: the level it stands in, its block
 * transformed and a cooked item rested in its cell. A held flame's player
 * finishes a block through its channel host, and a Molten cell, which no
 * player channels, through its level alone.
 * molten-cooks-the-block-below-it-as-flames-cooks-blocks
 */
@FunctionalInterface
public interface BlockFinishHost {

    /**
     * The level the host's blocks stand in.
     *
     * @return the server level
     */
    ServerLevel level();

    /**
     * Transforms the block at a position into another, which every watching
     * client draws as the old block mingling into the new
     * (decision petrify-stone-encasement-and-calcify-map).
     *
     * @param pos the position
     * @param to  the state it becomes
     */
    default void transformBlock(BlockPos pos, BlockState to) {
        BlockBreakHost.transform(level(), pos, to);
    }

    /**
     * Leaves an item at rest at a point, unthrown, where a cooked block's
     * result stands in the cell it emptied
     * (decision blocks-that-cook-into-items-cook-like-blocks).
     *
     * @param at    the point
     * @param stack the item
     */
    default void restItem(Vec3 at, ItemStack stack) {
        ItemEntity item = new ItemEntity(level(), at.x, at.y, at.z, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level().addFreshEntity(item);
    }
}
