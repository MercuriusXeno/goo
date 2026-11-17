package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * Layouts a fall needs three blocks of open air for: a stone pillar three
 * high, its top cell walled but for the east, over a walled stone floor cell
 * at the drop's foot; a lone stone block standing three blocks over a stone
 * floor, a wall that ends over open air; and a lone stone block hung three
 * blocks over a stone floor, an overhang over open floor.
 * falling-layer-lands-when-its-drip-arrives
 */
final class TallDrop {

    /** The blocks of open air a layer falls through. */
    static final int DROP = 3;
    /** The cell on the pillar's top. */
    static final BlockPos ON_THE_PILLAR = new BlockPos(2, 1 + DROP, 2);
    /** The floor cell at the drop's foot, east of the pillar, where a pour off it lands. */
    static final BlockPos AT_THE_FOOT = new BlockPos(3, 1, 2);
    /** A lone stone block standing over open air, its west face a wall that ends under it. */
    static final BlockPos LONE_WALL = new BlockPos(6, 1 + DROP, 6);
    /** A lone stone block hung over open floor, its underside an overhang. */
    static final BlockPos OVERHANG = new BlockPos(10, 1 + DROP, 6);

    private TallDrop() {
    }

    /**
     * Lays the pillar and the walled floor cell at its foot.
     *
     * @param helper the gametest helper
     */
    static void layPillar(GameTestHelper helper) {
        for (int up = 1; up < ON_THE_PILLAR.getY(); up++) {
            helper.setBlock(ON_THE_PILLAR.atY(up), Blocks.STONE);
            helper.setBlock(AT_THE_FOOT.atY(up + 1), Blocks.AIR);
        }
        wallSidesBut(helper, ON_THE_PILLAR, Direction.EAST);
        helper.setBlock(ON_THE_PILLAR, Blocks.AIR);
        helper.setBlock(ON_THE_PILLAR.east(), Blocks.AIR);
        helper.setBlock(AT_THE_FOOT.below(), Blocks.STONE);
        wallSidesBut(helper, AT_THE_FOOT, Direction.WEST);
        helper.setBlock(AT_THE_FOOT, Blocks.AIR);
    }

    /**
     * Lays a stone block over a column of open air, the cells around the
     * column open too, down to a stone floor reaching a cell past the foot.
     *
     * @param helper the gametest helper
     * @param block  the block, relative to the test
     * @param column the side of the block the open column stands on, or DOWN for under it
     * @return the floor cell at the column's foot
     */
    static BlockPos layOverOpenAir(GameTestHelper helper, BlockPos block, Direction column) {
        BlockPos top = column == Direction.DOWN ? block.below() : block.relative(column);
        BlockPos foot = top.atY(1);
        // the column and the cells around it stand open, so no wall of the bay's own touches the falling layer
        for (int y = foot.getY(); y <= top.getY(); y++) {
            helper.setBlock(top.atY(y), Blocks.AIR);
            for (Direction side : Direction.Plane.HORIZONTAL) {
                helper.setBlock(top.atY(y).relative(side), Blocks.AIR);
            }
        }
        helper.setBlock(block, Blocks.STONE);
        // a floor wider than the foot, so a layer of goo clinging to nothing lands on it and stays
        helper.setBlock(foot.below(), Blocks.STONE);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(foot.below().relative(side), Blocks.STONE);
        }
        return foot;
    }

    private static void wallSidesBut(GameTestHelper helper, BlockPos cell, Direction open) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != open) {
                helper.setBlock(cell.relative(side), Blocks.STONE);
            }
        }
    }
}
