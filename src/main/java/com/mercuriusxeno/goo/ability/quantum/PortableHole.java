package com.mercuriusxeno.goo.ability.quantum;

import com.mercuriusxeno.goo.block.quantum.PhasedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Opens a portable hole: a square tunnel from the struck block inward to a
 * depth goes out of phase, each held in a phased block until a game time puts it
 * back. A cell of air or fluid is passed over; a block that keeps a block
 * entity of its own, or that nothing can break, is left in phase.
 * portable-hole-phases-blocks-for-a-while
 */
public final class PortableHole {

    private PortableHole() {
    }

    /**
     * Phases a square tunnel running into the struck surface: each step
     * inward, the square of cells within the radius of the line across it.
     *
     * @param level    the level
     * @param struck   the struck block, the tunnel's first centre cell
     * @param inward   the direction the tunnel runs, into the struck face
     * @param depth    how many cells the tunnel runs
     * @param radius   how many cells the square reaches out from the line, 0 for the line alone
     * @param lifetime the ticks before each cell steps back into phase
     * @return the cells put out of phase
     */
    public static int open(ServerLevel level, BlockPos struck, Direction inward, int depth, int radius,
                           int lifetime) {
        long restoresAt = level.getGameTime() + lifetime;
        int phased = 0;
        for (BlockPos cell : tunnel(struck, inward, depth, radius)) {
            BlockState original = level.getBlockState(cell);
            if (phases(level, cell, original)) {
                level.setBlock(cell, GooBlocks.PHASED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(cell) instanceof PhasedBlockEntity held) {
                    held.hold(original, restoresAt);
                    phased++;
                }
            }
        }
        return phased;
    }

    /**
     * The cells of a square tunnel running from a block in a direction.
     *
     * @param start  the tunnel's first centre cell
     * @param inward the direction it runs
     * @param depth  how many cells it runs
     * @param radius how many cells the square reaches out from the line
     * @return the cells, nearest step first
     */
    static List<BlockPos> tunnel(BlockPos start, Direction inward, int depth, int radius) {
        Direction[] across = acrossOf(inward);
        List<BlockPos> cells = new ArrayList<>();
        for (int step = 0; step < depth; step++) {
            BlockPos centre = start.relative(inward, step);
            for (int first = -radius; first <= radius; first++) {
                for (int second = -radius; second <= radius; second++) {
                    cells.add(centre.relative(across[0], first).relative(across[1], second));
                }
            }
        }
        return cells;
    }

    private static Direction[] acrossOf(Direction inward) {
        return Arrays.stream(Direction.values())
                .filter(direction -> direction.getAxis() != inward.getAxis()
                        && direction.getAxisDirection() == Direction.AxisDirection.POSITIVE)
                .toArray(Direction[]::new);
    }

    /**
     * Whether a block goes out of phase: a solid block standing alone, which
     * the phased block can hold whole and put back as it was.
     *
     * @param level    the level
     * @param cell     its cell
     * @param original the block
     * @return true for a block the hole phases
     */
    static boolean phases(ServerLevel level, BlockPos cell, BlockState original) {
        return !original.isAir()
                && original.getFluidState().isEmpty()
                && !original.hasBlockEntity()
                && original.getDestroySpeed(level, cell) >= 0.0F;
    }
}
