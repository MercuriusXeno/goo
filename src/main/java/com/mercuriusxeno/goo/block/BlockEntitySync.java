package com.mercuriusxeno.goo.block;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Shared sync utility for goo block entities. Marks dirty for chunk saving
 * and sends an update packet to tracking clients.
 */
public final class BlockEntitySync {

    /** Block update flags: notify neighbors + send to clients. */
    public static final int BLOCK_UPDATE_FLAGS = 3;

    /** Bit shift from block coords to chunk coords (chunk = 16 blocks wide). */
    private static final int CHUNK_SHIFT = 4;

    private BlockEntitySync() {}

    /**
     * Marks the block entity dirty and sends a block update to tracking clients.
     * If the BE is an {@link IGooLightSource}, also schedules a lighting
     * recompute so emission tied to BE contents propagates without needing
     * a dedicated state property.
     *
     * @param be the block entity to sync
     */
    public static void markDirtyAndSync(BlockEntity be) {
        be.setChanged();
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(
                be.getBlockPos(), be.getBlockState(), be.getBlockState(), BLOCK_UPDATE_FLAGS);
            if (be instanceof IGooLightSource) {
                kickLighting(be);
            }
        }
    }

    /**
     * Reopens the chunk section's light gate and enqueues an emission
     * recompute for the given BE position, on whichever side the level runs.
     *
     * <p>The client needs its own recompute: the server sends light update
     * packets only to players on the view-distance edge
     * ({@code ChunkMap.getPlayers(pos, true)} in
     * {@code ChunkHolder.broadcastChanges}), so a client near the BE lights
     * it with its own engine, which {@code LevelRenderer} drains each frame
     * (decision diagnose-then-fix-vat-stale-light).
     *
     * <p>{@code setLightEnabled} bypasses the section-level "no light sources"
     * gate that's set during initial chunk-load light propagation. BE NBT
     * loads after blockstate placement, so {@code gooLightEmission} reads 0
     * during that scan and the section is marked source-free.
     * {@code checkBlock} enqueues the recompute and the threaded engine
     * drains its own queue on its tick, which is what propagates the new
     * emission. A manual drain through {@code runLightUpdates} throws on a
     * server, since the server engine runs its updates on the chunk thread
     * (decision light-kick-never-drains).
     *
     * @param be the BE whose chunk section needs lighting reopened
     */
    public static void kickLighting(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        ChunkPos chunkPos = new ChunkPos(
            be.getBlockPos().getX() >> CHUNK_SHIFT,
            be.getBlockPos().getZ() >> CHUNK_SHIFT);
        level.getLightEngine().setLightEnabled(chunkPos, true);
        level.getLightEngine().checkBlock(be.getBlockPos());
    }

    /**
     * Call from {@code BlockEntity.onLoad()} on {@link IGooLightSource} BEs
     * so post-NBT-load goo contents propagate emission. The chunk-load light
     * scan runs before BE NBT load (emission reads 0 then), so any BE that
     * loads with goo already in it would otherwise stay dark forever.
     *
     * <p>Gated on {@code gooLightEmission() > 0}: an empty BE doesn't need
     * to open its section gate, and an unconditional kick from every goo BE
     * on every chunk load would needlessly enqueue a recompute once per BE
     * in the chunk.
     *
     * @param be the BE whose post-load emission needs propagating
     */
    public static void kickLightingOnLoad(BlockEntity be) {
        if (be instanceof IGooLightSource src && src.gooLightEmission() > 0) {
            kickLighting(be);
        }
    }

    /**
     * Call from {@code BlockEntity.onDataPacket} on {@link IGooLightSource}
     * BEs, after the packet's contents are loaded. The data packet is the
     * only word a nearby client gets of new goo contents, and loading them
     * rechecks no light, so the client's own engine keeps the old emission
     * until this recompute (decision diagnose-then-fix-vat-stale-light).
     *
     * @param be the BE whose contents just arrived
     */
    public static void relightOnContentsArrived(BlockEntity be) {
        if (be instanceof IGooLightSource) {
            kickLighting(be);
        }
    }

    /**
     * Invalidates cached capabilities so listeners re-query. Server-side only.
     *
     * @param be the block entity whose capabilities to invalidate
     */
    public static void invalidateCapabilities(BlockEntity be) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.invalidateCapabilities(be.getBlockPos());
        }
    }

    /**
     * Pushes an integer value into a blockstate property. Server-side only.
     *
     * @param be       the block entity owning the blockstate
     * @param property the integer property to update
     * @param value    the new value
     */
    public static void syncIntProperty(BlockEntity be, IntegerProperty property, int value) {
        Level level = be.getLevel();
        if (level != null && !level.isClientSide()) {
            level.setBlock(be.getBlockPos(),
                be.getBlockState().setValue(property, value), BLOCK_UPDATE_FLAGS);
        }
    }
}
