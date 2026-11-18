package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What an Infest cell gnaws and bites, and when: the block it lies on and
 * the block behind each side it coats, each living thing standing in it, on
 * the game ticks its cadence falls on, so a cell woken between them by a
 * pour or a changed neighbour gnaws nothing extra.
 * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it
 */
public final class InfestGnaw {

    /** The sides a cell can coat, its four walls and its ceiling, in the order it gnaws behind them. */
    private static final List<Direction> COATABLE_SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH,
            Direction.WEST, Direction.UP);

    private InfestGnaw() {
    }

    /**
     * The blocks a cell gnaws: the one under it, then the one behind each side it coats.
     *
     * @param cell    the cell
     * @param coated  whether the cell coats a side
     * @return the blocks, the one under the cell first
     */
    public static List<BlockPos> gnawedBlocks(BlockPos cell, Predicate<Direction> coated) {
        List<BlockPos> gnawed = new ArrayList<>();
        gnawed.add(cell.below());
        for (Direction side : COATABLE_SIDES) {
            if (coated.test(side)) {
                gnawed.add(cell.relative(side));
            }
        }
        return gnawed;
    }

    /**
     * Whether a living thing stands in a cell: its feet are in the cell, or the cell holds it up.
     *
     * @param cell      the cell
     * @param feet      the block the thing's feet are in
     * @param underFeet the block holding the thing up
     * @return true where the thing stands in the cell
     */
    public static boolean standsIn(BlockPos cell, BlockPos feet, BlockPos underFeet) {
        return cell.equals(feet) || cell.equals(underFeet);
    }

    /**
     * Whether a game tick falls on a cell's cadence.
     *
     * @param gameTime the level's game time
     * @param ticks    the ticks between gnaws
     * @return true on a tick the cell gnaws and bites
     */
    public static boolean gnawsAt(long gameTime, int ticks) {
        return gameTime % ticks == 0;
    }

    /**
     * The ticks from a game tick to the next one on a cell's cadence.
     *
     * @param gameTime the level's game time
     * @param ticks    the ticks between gnaws
     * @return the delay, a whole cadence where the tick falls on one
     */
    public static int ticksToNextGnaw(long gameTime, int ticks) {
        return ticks - (int) (gameTime % ticks);
    }
}
