package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * A host whose steps read the blocks around it and break them, the mundane
 * blocks rock's abilities cut through: a held channel's player and a thrown
 * blob's landing (decisions flatten-disc-cursor-breaks-above-the-plane,
 * bore-vortex-with-a-worldspace-shake, crush-blob-breaks-along-its-strike).
 */
public interface BlockBreakHost extends StepHost {

    /**
     * Whether the block at a position belongs to a block tag.
     *
     * @param pos the position
     * @param tag the block tag
     * @return true where the block standing there is in the tag
     */
    boolean blockIn(BlockPos pos, TagKey<Block> tag);

    /**
     * Whether a position holds air.
     *
     * @param pos the position
     * @return true where no block stands
     */
    boolean airAt(BlockPos pos);

    /**
     * Breaks a block, dropping its loot as a pickaxe harvests it and playing
     * its break particles.
     *
     * @param pos the block
     */
    void breakBlock(BlockPos pos);

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
}
