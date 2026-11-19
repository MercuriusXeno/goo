package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.SculkBehaviour;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Whether a sculk charge on Creep goo is stranded: no Creep cell it can step
 * to turns the block under it, and no step leads off the goo onto sculk or
 * a vein. The walk takes the moves vanilla's charge cursor takes, to the
 * eighteen neighbours sharing a face or an edge, past no sturdy corner, onto
 * blocks answering SculkBehaviour, and runs over a coat of any size: it
 * stops at the first cell turning its block or step off the goo, so a free
 * charge pays only the walk to the nearest of them, and a stranded charge
 * pays one whole walk and ends.
 * sculk-charge-runs-along-host-goo-without-weakening
 */
final class CreepReach {

    /** The neighbours a charge cursor steps to: every offset in the cube around it but its corners and itself. */
    private static final List<Vec3i> STEPS = BlockPos.betweenClosedStream(new BlockPos(-1, -1, -1),
                    new BlockPos(1, 1, 1))
            .filter(offset -> (offset.getX() == 0 || offset.getY() == 0 || offset.getZ() == 0)
                    && !offset.equals(BlockPos.ZERO))
            .map(BlockPos::immutable).map(Vec3i.class::cast).toList();

    /**
     * What a walk found.
     *
     * @param stranded whether the charge can do nothing more and go nowhere off the goo
     * @param visited  the Creep cells the walk reached, every one sharing the verdict where the charge is free
     */
    record Walk(boolean stranded, Set<BlockPos> visited) {
    }

    private CreepReach() {
    }

    /**
     * Walks the Creep cells a charge can reach from where it stands.
     *
     * @param level          the level
     * @param start          where the charge stands
     * @param turnsSomething whether a Creep cell turns the block under it
     * @return whether the charge is stranded, and the cells the walk reached
     */
    static Walk walk(LevelAccessor level, BlockPos start, Predicate<BlockPos> turnsSomething) {
        Deque<BlockPos> open = new ArrayDeque<>(List.of(start.immutable()));
        Set<BlockPos> seen = new HashSet<>(open);
        while (!open.isEmpty()) {
            BlockPos cell = open.poll();
            if (turnsSomething.test(cell)) {
                return new Walk(false, seen);
            }
            List<BlockPos> steps = stepsFrom(level, cell);
            if (steps.stream().anyMatch(next -> !(level.getBlockState(next).getBlock() instanceof CreepCellBlock))) {
                return new Walk(false, seen);
            }
            steps.stream().filter(seen::add).forEach(open::add);
        }
        return new Walk(true, seen);
    }

    /**
     * The neighbours a charge cursor standing in a cell can step to: those
     * answering SculkBehaviour, past no sturdy corner.
     *
     * @param level the level
     * @param cell  where the cursor stands
     * @return the neighbours it can step to
     */
    private static List<BlockPos> stepsFrom(LevelAccessor level, BlockPos cell) {
        return STEPS.stream().map(cell::offset)
                .filter(next -> level.getBlockState(next).getBlock() instanceof SculkBehaviour)
                .filter(next -> unobstructed(level, cell, next)).toList();
    }

    /**
     * Whether a cursor can step between two neighbours, as vanilla's charge
     * cursor judges it: a face neighbour always, an edge neighbour where one
     * of the two blocks it would pass beside has no sturdy face toward it.
     *
     * @param level the level
     * @param from  where the cursor stands
     * @param to    the neighbour it would step to
     * @return true where the step is open
     */
    private static boolean unobstructed(LevelAccessor level, BlockPos from, BlockPos to) {
        if (from.distManhattan(to) == 1) {
            return true;
        }
        BlockPos delta = to.subtract(from);
        // the two sides an edge step passes beside are those pointing along it
        return Direction.stream()
                .filter(side -> side.getStepX() * delta.getX() + side.getStepY() * delta.getY()
                        + side.getStepZ() * delta.getZ() > 0)
                .anyMatch(side -> !level.getBlockState(from.relative(side))
                        .isFaceSturdy(level, from.relative(side), side.getOpposite()));
    }
}
