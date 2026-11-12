package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.BlockTransformPayload;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
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

    /** Chunks of debris one broken block throws. */
    int DEBRIS_CHUNKS = 12;
    /** How far from a broken block's center its debris starts, each way. */
    double DEBRIS_SPREAD = 0.3;
    /** How fast its debris flies out. */
    double DEBRIS_SPEED = 0.3;
    /** The highest breaker id a block's cracks travel under, below every entity's id. */
    int HIGHEST_CRACKER_ID = -1;

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
     * The state standing at a position.
     *
     * @param pos the position
     * @return the state there
     */
    default BlockState stateAt(BlockPos pos) {
        return level().getBlockState(pos);
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
     * Builds a block's exposure toward a state, the clients tinting the
     * mingled block (decision decay-gnats-degrade-each-block-once).
     *
     * @param pos    the block
     * @param toward the state it becomes at a full share
     * @param amount the share this exposure adds
     * @param tint   the RGB the clients tint the mingled block by
     * @return the share after it, 1 the tick the block steps
     */
    default float exposeBlock(BlockPos pos, BlockState toward, float amount, int tint) {
        return GooServerState.of(level().getServer()).blockExposures().expose(level(), pos, toward, amount, tint);
    }

    /**
     * Leaves a block's built share to finish its step on its own, stepping
     * through the finish the hold would have run
     * (decisions decay-gnats-degrade-each-block-once, blocks-that-cook-into-items-cook-like-blocks).
     *
     * @param pos    the block
     * @param rate   the share it builds each tick
     * @param finish the step the block takes once full
     */
    default void finishBlockAlone(BlockPos pos, float rate, Runnable finish) {
        GooServerState.of(level().getServer()).blockExposures().finishAlone(level(), pos, rate, finish);
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

    /**
     * Whether a block is finishing its step on its own.
     *
     * @param pos the block
     * @return true from {@link #finishBlockAlone} until it steps
     */
    default boolean finishingAlone(BlockPos pos) {
        return GooServerState.of(level().getServer()).blockExposures().finishingAt(level(), pos);
    }

    /**
     * Draws the vanilla block break texture on a block at a stage, seen by
     * every player near it, the breaking player too.
     * decision gradual-breaks-share-one-break-texture-piece
     *
     * @param pos   the block
     * @param stage the crack stage, 0 to 9, or {@link GradualBreak#CLEARED} to clear it
     */
    default void crackBlock(BlockPos pos, int stage) {
        level().destroyBlockProgress(crackerId(pos), pos, stage);
    }

    /**
     * The breaker id a block's cracks travel under. A client keeps one crack
     * per breaker id and the server skips the player whose id it is, so each
     * block takes its own negative id, one no entity holds.
     *
     * @param pos the block
     * @return the id, below zero
     */
    static int crackerId(BlockPos pos) {
        return HIGHEST_CRACKER_ID - (Long.hashCode(pos.asLong()) & Integer.MAX_VALUE);
    }

    /**
     * Tells the watching clients a block stepped from one state to another,
     * as a transform does, for a step that set the block some other way; a
     * cell the clients drew melting away stops drawing the melt.
     * vacuumed-residuals-decay-through-mingle-noise-before-breaking
     *
     * @param pos  the block
     * @param from the state it was
     * @param to   the state it is now
     */
    default void sendTransform(BlockPos pos, BlockState from, BlockState to) {
        new BlockTransformPayload(pos, Block.getId(from), Block.getId(to)).sendToTracking(level());
    }

    /**
     * Plays a slurp at a block's middle, heard by everyone near.
     * vacuum-sounds-a-suction-wind-and-rising-slurps
     *
     * @param pos   the block
     * @param pitch the slurp's pitch
     */
    default void slurpAt(BlockPos pos, float pitch) {
        level().playSound(null, pos, GooSounds.VACUUM_SLURP.get(), SoundSource.PLAYERS, 1f, pitch);
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
