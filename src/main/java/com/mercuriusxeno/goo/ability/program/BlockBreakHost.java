package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockTransformPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A host whose steps read the blocks around it and break them, the mundane
 * blocks rock's abilities cut through: a held channel's player and a thrown
 * blob's landing (decisions flatten-disc-cursor-breaks-above-the-plane,
 * bore-vortex-with-a-worldspace-shake, crush-blob-breaks-along-its-strike).
 */
public interface BlockBreakHost extends StepHost {

    /** Chunks of debris one crushed block throws. */
    int DEBRIS_CHUNKS = 12;
    /** How far from a crushed block's center its debris starts, each way. */
    double DEBRIS_SPREAD = 0.3;
    /** How fast its debris flies out. */
    double DEBRIS_SPEED = 0.3;

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
     * Throws a block's debris up and out of it as it breaks: chunks of the
     * block flung from its center (decision crush-blob-breaks-along-its-strike).
     *
     * @param pos the block
     */
    default void throwDebris(BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos);
        level().sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level().getBlockState(pos)),
                center.x, center.y, center.z, DEBRIS_CHUNKS, DEBRIS_SPREAD, DEBRIS_SPREAD, DEBRIS_SPREAD,
                DEBRIS_SPEED);
    }

    /**
     * Builds a block's exposure toward the state it calcifies into
     * (decision petrify-stone-encasement-and-calcify-map).
     *
     * @param pos    the block
     * @param toward the state it becomes at a full share
     * @param amount the share this exposure adds
     * @return the share after it, 1 the tick the block is due to step its rung
     */
    default float exposeBlock(BlockPos pos, BlockState toward, float amount) {
        return GooServerState.of(level().getServer()).blockExposures().expose(level(), pos, toward, amount);
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
