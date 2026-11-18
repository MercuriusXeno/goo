package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground a Build grows stone over: a rectangle of ground cells on the
 * pinned corner's level, the drag sizing it, and the rise the pitch sets,
 * each capped by the ability's {@link BuildBounds}.
 * decision spire-rips-walls-and-platforms
 * decision spire-grows-stone-out-of-the-top
 *
 * @param corner   the ground cell the press pinned
 * @param opposite the ground cell across the footprint, on the corner's level
 * @param rise     how many stone blocks grow above each ground cell
 */
public record BuildFootprint(BlockPos corner, BlockPos opposite, int rise) {

    /**
     * The same footprint at another rise.
     *
     * @param lower the rise
     * @return the footprint growing that high
     */
    public BuildFootprint withRise(int lower) {
        return new BuildFootprint(corner, opposite, lower);
    }

    /**
     * The ground cells of the footprint, the block each column of stone grows on.
     *
     * @return the cells, row by row
     */
    public List<BlockPos> groundCells() {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos cell : BlockPos.betweenClosed(corner, opposite)) {
            cells.add(cell.immutable());
        }
        return cells;
    }

    /**
     * The footprint's lowest corner, the minimum on every axis.
     *
     * @return the lowest corner
     */
    public BlockPos min() {
        return new BlockPos(Math.min(corner.getX(), opposite.getX()), corner.getY(),
                Math.min(corner.getZ(), opposite.getZ()));
    }

    /**
     * The footprint's highest corner, the maximum on every axis.
     *
     * @return the highest corner
     */
    public BlockPos max() {
        return new BlockPos(Math.max(corner.getX(), opposite.getX()), corner.getY(),
                Math.max(corner.getZ(), opposite.getZ()));
    }
}
