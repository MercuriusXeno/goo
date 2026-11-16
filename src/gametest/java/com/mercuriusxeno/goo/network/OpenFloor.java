package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;
import java.util.List;

/**
 * A square of open stone floor a test raises a cell on, wide enough that the
 * cell's side cells and the cells past them all stand over stone with open
 * air above, so a pour goes wherever the goo's rule sends it.
 * cohesion-is-the-step-the-goo-holds
 */
final class OpenFloor {

    /** Cells from the middle to the square's edge: the side cells and one ring past them. */
    private static final int REACH = 2;
    /** Open air laid over the floor, above the tallest cell a test raises. */
    private static final int HEADROOM = 2;

    private OpenFloor() {
    }

    /**
     * Lays the open floor around a middle cell, stone under each cell and air in and above it.
     *
     * @param helper the gametest helper
     * @param middle the cell the test raises, relative to the test
     */
    static void layAround(GameTestHelper helper, BlockPos middle) {
        for (int east = -REACH; east <= REACH; east++) {
            for (int south = -REACH; south <= REACH; south++) {
                BlockPos cell = middle.offset(east, 0, south);
                helper.setBlock(cell.below(), Blocks.STONE);
                for (int up = 0; up < HEADROOM; up++) {
                    helper.setBlock(cell.above(up), Blocks.AIR);
                }
            }
        }
    }

    /**
     * The middle cell and its four side cells.
     *
     * @param middle the middle cell
     * @return the five cells, the middle first, then north, east, south and west
     */
    static List<BlockPos> crossAt(BlockPos middle) {
        List<BlockPos> cross = new ArrayList<>(List.of(middle));
        Direction.Plane.HORIZONTAL.forEach(side -> cross.add(middle.relative(side)));
        return cross;
    }

    /**
     * The layers each cell of a list holds.
     *
     * @param helper the gametest helper
     * @param cells  the cells, relative to the test
     * @return each cell's layers, 0 for anything but a volume cell
     */
    static List<Integer> layersIn(GameTestHelper helper, List<BlockPos> cells) {
        return cells.stream().map(cell -> VolumeCellBlock.layersOf(helper.getBlockState(cell))).toList();
    }
}
