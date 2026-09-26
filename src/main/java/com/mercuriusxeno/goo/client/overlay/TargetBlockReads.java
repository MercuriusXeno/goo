package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * What the highlight reads off the aimed-at block: the chain marker there
 * or beside it, whether it takes another blob, its goo type, and whether
 * the block is a water source (decision render-context-is-the-one-emitter).
 */
final class TargetBlockReads {

    private TargetBlockReads() {
    }

    /**
     * Reads the goo type from a chain marker BE, or null if unavailable.
     *
     * @param level the current level
     * @param pos   the block position
     * @return the marker's goo type, or null
     */
    static @Nullable ResourceKey<GooTypeDefinition> markerGooType(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return null;
        }
        return be.getGooType();
    }

    /**
     * Returns true if the block at the given position is a water source.
     *
     * @param level the client level
     * @param pos   the block position
     * @return true if water source
     */
    static boolean isWaterSource(Level level, BlockPos pos) {
        return level.getFluidState(pos).isSource()
                && level.getFluidState(pos).getType() == Fluids.WATER;
    }

    /**
     * The chain marker at the hit block or on the hit face beside it,
     * where a thrown blob would place one.
     *
     * @param level the client level
     * @param pos   the hit block position
     * @param face  the hit face
     * @return the chain marker position, or null
     */
    static @Nullable BlockPos adjacentMarker(Level level, BlockPos pos, Direction face) {
        if (level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity) {
            return pos;
        }
        BlockPos adj = pos.relative(face);
        if (level.getBlockEntity(adj) instanceof ChainMarkerBlockEntity) {
            return adj;
        }
        return null;
    }

    /**
     * Returns true if the chain marker at the given position can still
     * accept more blobs (not at max stacks, no active fuse or behavior).
     *
     * @param level the client level
     * @param pos   the chain marker position
     * @return true if more blobs can be stacked
     */
    static boolean canAcceptMoreBlobs(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ChainMarkerBlockEntity be)) {
            return false;
        }
        return be.getBehavior() == null && be.getStackCount() < be.getMaxStacks();
    }
}
