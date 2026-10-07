package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockTransformPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A host whose steps read the blocks around it and break them, the mundane
 * blocks rock's abilities cut through: a held channel's player and a thrown
 * blob's landing (decisions flatten-disc-cursor-breaks-above-the-plane,
 * bore-vortex-with-a-worldspace-shake, crush-blob-breaks-along-its-strike).
 */
public interface BlockBreakHost extends StepHost {

    /**
     * The level the host's blocks stand in.
     *
     * @return the server level
     */
    ServerLevel level();

    /**
     * The entity breaking the blocks, which their loot and break event credit.
     *
     * @return the breaker, or null for a blob's landing
     */
    default @Nullable Entity breaker() {
        return null;
    }

    /**
     * Whether the block at a position belongs to a block tag.
     *
     * @param pos the position
     * @param tag the block tag
     * @return true where the block standing there is in the tag
     */
    default boolean blockIn(BlockPos pos, TagKey<Block> tag) {
        return level().getBlockState(pos).is(tag);
    }

    /**
     * Whether a position holds air.
     *
     * @param pos the position
     * @return true where no block stands
     */
    default boolean airAt(BlockPos pos) {
        return level().getBlockState(pos).isAir();
    }

    /**
     * The block standing at a position.
     *
     * @param pos the position
     * @return the block there
     */
    default Block blockAt(BlockPos pos) {
        return level().getBlockState(pos).getBlock();
    }

    /**
     * Transforms the block at a position into another, which every watching
     * client draws as the old block mingling into the new
     * (decision petrify-stone-encasement-and-calcify-map).
     *
     * @param pos the position
     * @param to  the state it becomes
     */
    default void transformBlock(BlockPos pos, BlockState to) {
        transform(level(), pos, to);
    }

    /**
     * Breaks a block, dropping its loot as a pickaxe harvests it and playing
     * its break particles.
     *
     * @param pos the block
     */
    default void breakBlock(BlockPos pos) {
        harvest(level(), pos, breaker());
    }

    /**
     * Breaks a block in a level and drops its loot as a pickaxe would harvest
     * it, so stone yields cobblestone where a glove or a blob would yield
     * nothing.
     *
     * @param level   the server level
     * @param pos     the block
     * @param breaker the entity breaking it, or null for a blob's landing
     */
    static void harvest(ServerLevel level, BlockPos pos, @Nullable Entity breaker) {
        ItemStack harvestTool = new ItemStack(Items.IRON_PICKAXE);
        Block.dropResources(level.getBlockState(pos), level, pos, level.getBlockEntity(pos), breaker, harvestTool);
        level.destroyBlock(pos, false, breaker);
    }

    /**
     * Transforms a block in a level and tells the watching clients to draw the
     * old block mingling into the new.
     *
     * @param level the server level
     * @param pos   the block
     * @param to    the state it becomes
     */
    static void transform(ServerLevel level, BlockPos pos, BlockState to) {
        BlockState from = level.getBlockState(pos);
        level.setBlock(pos, to, Block.UPDATE_ALL);
        new BlockTransformPayload(pos, Block.getId(from), Block.getId(to))
                .sendToTracking(level);
    }
}
