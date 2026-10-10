package com.mercuriusxeno.goo.ability.growth;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * How Growth grows a vine: now and then it hangs a block longer, a vine
 * below on the same faces, or creeps sideways or up along a wall it clings
 * to, where that wall goes on and the cell is empty. Vanilla's random tick
 * barely grows a vine, rolling to act one tick in four and refusing to
 * spread once a few vines stand near, so the breeze grows them itself, past
 * that crowding.
 * growth-breeze-ticks-plants
 */
public final class VineGrowth {

    /** One in this many held ticks a vine under the breeze grows. */
    static final int GROWTH_ODDS = 6;

    private VineGrowth() {
    }

    /**
     * Grows a vine under the breeze, by chance: down, or along its wall.
     *
     * @param level  the server level
     * @param pos    the vine's cell
     * @param vine   the vine
     * @param random the random source
     * @return true when the vine grew a block
     */
    public static boolean grow(ServerLevel level, BlockPos pos, BlockState vine, RandomSource random) {
        if (random.nextInt(GROWTH_ODDS) != 0) {
            return false;
        }
        List<Growth> options = growthsOf(level, pos, vine);
        if (options.isEmpty()) {
            return false;
        }
        Growth chosen = options.get(random.nextInt(options.size()));
        level.setBlock(chosen.cell(), chosen.vine(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * A block of vine a vine can grow.
     *
     * @param cell the empty cell it grows into
     * @param vine the vine it grows there
     */
    record Growth(BlockPos cell, BlockState vine) {
    }

    /**
     * Every block a vine can grow now: below it on its own faces, and beside
     * or above it on each wall it clings to where that wall goes on.
     *
     * @param level the level
     * @param pos   the vine's cell
     * @param vine  the vine
     * @return the growths open to it
     */
    static List<Growth> growthsOf(BlockGetter level, BlockPos pos, BlockState vine) {
        List<Growth> options = new ArrayList<>();
        List<Direction> walls = wallsOf(vine);
        BlockPos below = pos.below();
        if (!walls.isEmpty() && level.getBlockState(below).isAir()) {
            options.add(new Growth(below, onWalls(walls)));
        }
        for (Direction wall : walls) {
            options.addAll(creepsAlong(level, pos, wall));
        }
        return options;
    }

    /**
     * The blocks a vine can creep into along one wall: beside it either way
     * and above it, where the cell is empty and the wall goes on there.
     *
     * @param level the level
     * @param pos   the vine's cell
     * @param wall  the side the wall it clings to stands on
     * @return the creeps open to it
     */
    private static List<Growth> creepsAlong(BlockGetter level, BlockPos pos, Direction wall) {
        return List.of(wall.getClockWise(), wall.getCounterClockWise(), Direction.UP).stream()
                .map(pos::relative)
                .filter(cell -> level.getBlockState(cell).isAir()
                        && VineBlock.isAcceptableNeighbour(level, cell.relative(wall), wall))
                .map(cell -> new Growth(cell, onWalls(List.of(wall))))
                .toList();
    }

    private static List<Direction> wallsOf(BlockState vine) {
        return Direction.Plane.HORIZONTAL.stream().filter(side -> vine.getValue(VineBlock.getPropertyForFace(side)))
                .toList();
    }

    private static BlockState onWalls(List<Direction> walls) {
        BlockState vine = Blocks.VINE.defaultBlockState();
        for (Direction wall : walls) {
            vine = vine.setValue(VineBlock.getPropertyForFace(wall), true);
        }
        return vine;
    }
}
