package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * A stone pillar's top cell, walled but for the east, where a drop of one
 * block falls to a walled stone top: a layer poured off the pillar's ledge
 * lands in the cell over that top and nowhere else.
 * adhesion-is-a-ladder-of-holds
 */
final class LedgeDrop {

    /** The cell on the pillar's top. */
    static final BlockPos ON_THE_PILLAR = new BlockPos(2, 2, 2);
    /** The cell at the foot of the drop, east of the pillar, where a poured layer lands. */
    static final BlockPos BELOW_THE_DROP = ON_THE_PILLAR.east().below();

    private LedgeDrop() {
    }

    /**
     * Lays the pillar, its walls, the open drop and the walled top at its foot.
     *
     * @param helper the gametest helper
     */
    static void lay(GameTestHelper helper) {
        helper.setBlock(ON_THE_PILLAR.below(), Blocks.STONE);
        wallSidesBut(helper, ON_THE_PILLAR, Direction.EAST);
        helper.setBlock(ON_THE_PILLAR, Blocks.AIR);
        helper.setBlock(ON_THE_PILLAR.above(), Blocks.AIR);
        helper.setBlock(ON_THE_PILLAR.east(), Blocks.AIR);
        helper.setBlock(BELOW_THE_DROP.below(), Blocks.STONE);
        wallSidesBut(helper, BELOW_THE_DROP, Direction.WEST);
        helper.setBlock(BELOW_THE_DROP, Blocks.AIR);
    }

    private static void wallSidesBut(GameTestHelper helper, BlockPos cell, Direction open) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != open) {
                helper.setBlock(cell.relative(side), Blocks.STONE);
            }
        }
    }
}
