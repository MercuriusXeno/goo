package com.mercuriusxeno.goo.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The ground a Build grows stone over: a rectangle of ground cells lying in
 * the plane of the face the press pinned, the drag sizing it, and the rise
 * the pitch sets, running out along that face's normal, each capped by the
 * ability's {@link BuildBounds}. A footprint on the ground rises up, one on a
 * wall pushes out sideways and one on a ceiling pushes down.
 * decision spire-rips-walls-and-platforms
 * decision spire-grows-stone-out-of-the-top
 * decision build-goes-sideways
 *
 * @param corner   the ground cell the press pinned
 * @param opposite the ground cell across the footprint, in the corner's plane
 * @param rise     how many stone blocks grow out of each ground cell
 * @param face     the face the press pinned, whose normal the rise runs along
 */
public record BuildFootprint(BlockPos corner, BlockPos opposite, int rise, Direction face) {

    /**
     * A footprint on the ground, rising up.
     *
     * @param corner   the ground cell the press pinned
     * @param opposite the ground cell across the footprint, on the corner's level
     * @param rise     how many stone blocks grow above each ground cell
     */
    public BuildFootprint(BlockPos corner, BlockPos opposite, int rise) {
        this(corner, opposite, rise, Direction.UP);
    }

    /**
     * The same footprint at another rise.
     *
     * @param lower the rise
     * @return the footprint growing that far
     */
    public BuildFootprint withRise(int lower) {
        return new BuildFootprint(corner, opposite, lower, face);
    }

    /**
     * The ground cells of the footprint, the block each column of stone grows out of.
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
        return new BlockPos(Math.min(corner.getX(), opposite.getX()), Math.min(corner.getY(), opposite.getY()),
                Math.min(corner.getZ(), opposite.getZ()));
    }

    /**
     * The footprint's highest corner, the maximum on every axis.
     *
     * @return the highest corner
     */
    public BlockPos max() {
        return new BlockPos(Math.max(corner.getX(), opposite.getX()), Math.max(corner.getY(), opposite.getY()),
                Math.max(corner.getZ(), opposite.getZ()));
    }
}
