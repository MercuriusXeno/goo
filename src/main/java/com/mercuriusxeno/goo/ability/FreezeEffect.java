package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@link BlockEffect} that converts a single block under frost rules:
 * water becomes magicked-ice, lava becomes obsidian, fire and plants
 * become air, and other blocks are left unchanged.
 */
public final class FreezeEffect implements BlockEffect {

    /** Singleton instance. */
    public static final FreezeEffect INSTANCE = new FreezeEffect();

    private FreezeEffect() {
    }

    @Override
    public boolean apply(ServerLevel level, BlockPos pos) {
        return convertBlock(level, pos);
    }

    /**
     * Converts a single block per the frost rules: water becomes
     * magicked ice, lava becomes obsidian, fire and plants become
     * air, and other blocks are left unchanged.
     *
     * @param level the server level
     * @param pos   the block position to convert
     * @return true if the block was converted; false if no conversion applied
     */
    private static boolean convertBlock(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block replacement = frostReplacement(state);
        if (replacement == null) {
            return false;
        }
        level.setBlock(pos, replacement.defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * Returns the block to replace with under frost rules, or null if no conversion applies.
     *
     * @param state the current block state
     * @return the replacement block, or null
     */
    private static Block frostReplacement(BlockState state) {
        if (state.is(Blocks.WATER)) {
            return GooBlocks.MAGICKED_ICE.get();
        }
        if (state.is(Blocks.LAVA)) {
            return Blocks.OBSIDIAN;
        }
        if (isFire(state) || isPlant(state)) {
            return Blocks.AIR;
        }
        return null;
    }

    /**
     * Returns true if the block state is any fire variant.
     *
     * @param state the block state to test
     * @return true if the block is fire or soul fire
     */
    private static boolean isFire(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE);
    }

    /**
     * Returns true if the block state is a destructible plant.
     * Covers vines, lily pads, grasses, flowers, and similar foliage.
     *
     * @param state the block state to test
     * @return true if this is a plant that frost destroys
     */
    private static boolean isPlant(BlockState state) {
        return isVineOrAquatic(state)
                || isGrassOrFern(state)
                || isSmallFlower(state)
                || isTallFlowerOrBush(state);
    }

    /**
     * Returns true if the state is a vine, lily pad, or aquatic plant (seagrass, kelp).
     *
     * @param state the block state to test
     * @return true if the block is a vine or aquatic plant
     */
    private static boolean isVineOrAquatic(BlockState state) {
        return state.is(Blocks.VINE)
                || state.is(Blocks.LILY_PAD)
                || isAquaticPlant(state);
    }

    /**
     * Returns true if the state is an aquatic plant (seagrass or kelp).
     *
     * @param state the block state to test
     * @return true if the block is an aquatic plant
     */
    private static boolean isAquaticPlant(BlockState state) {
        return state.is(Blocks.SEAGRASS)
                || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP)
                || state.is(Blocks.KELP_PLANT);
    }

    /**
     * Returns true if the state is a grass or fern variant.
     *
     * @param state the block state to test
     * @return true if the block is grass or fern
     */
    private static boolean isGrassOrFern(BlockState state) {
        return state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    /**
     * Returns true if the state is a single-block flower (dandelion, poppy, tulips, etc.).
     *
     * @param state the block state to test
     * @return true if the block is a small flower
     */
    private static boolean isSmallFlower(BlockState state) {
        return isCommonFlower(state)
                || isTulip(state)
                || isDaisyOrLater(state);
    }

    /**
     * Returns true if the state is one of the common single-block flowers (dandelion through azure bluet).
     *
     * @param state the block state to test
     * @return true if the block is a common flower
     */
    private static boolean isCommonFlower(BlockState state) {
        return state.is(Blocks.DANDELION)
                || state.is(Blocks.POPPY)
                || state.is(Blocks.BLUE_ORCHID)
                || state.is(Blocks.ALLIUM);
    }

    /**
     * Returns true if the state is azure bluet, oxeye daisy, cornflower, or lily of the valley.
     *
     * @param state the block state to test
     * @return true if the block is a late-palette flower
     */
    private static boolean isDaisyOrLater(BlockState state) {
        return state.is(Blocks.AZURE_BLUET)
                || state.is(Blocks.OXEYE_DAISY)
                || state.is(Blocks.CORNFLOWER)
                || state.is(Blocks.LILY_OF_THE_VALLEY);
    }

    /**
     * Returns true if the state is any tulip color variant.
     *
     * @param state the block state to test
     * @return true if the block is a tulip
     */
    private static boolean isTulip(BlockState state) {
        return state.is(Blocks.RED_TULIP)
                || state.is(Blocks.ORANGE_TULIP)
                || state.is(Blocks.WHITE_TULIP)
                || state.is(Blocks.PINK_TULIP);
    }

    /**
     * Returns true if the state is a tall flower, berry bush, or dead bush.
     *
     * @param state the block state to test
     * @return true if the block is a tall flower or bush
     */
    private static boolean isTallFlowerOrBush(BlockState state) {
        return isTallFlower(state)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.DEAD_BUSH);
    }

    /**
     * Returns true if the state is a two-block-tall flower (sunflower, lilac, rose bush, peony).
     *
     * @param state the block state to test
     * @return true if the block is a tall flower
     */
    private static boolean isTallFlower(BlockState state) {
        return state.is(Blocks.SUNFLOWER)
                || state.is(Blocks.LILAC)
                || state.is(Blocks.ROSE_BUSH)
                || state.is(Blocks.PEONY);
    }
}
