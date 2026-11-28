package com.mercuriusxeno.goo.block.alientech;

import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Where a light beacon's column runs: straight out the side opposite the face the beacon attaches by, through air and
 * light rail, stopping at the first other block. The client draws the column that far, and the server lights the
 * reflector it stops at.
 * decision beacon-column-into-a-reflector-counts-as-max-light
 */
public final class LightBeaconColumn {

    /** How far a column runs at most, in blocks: a vanilla beacon beam's drawn reach. */
    public static final int REACH = 2048;

    private LightBeaconColumn() {
    }

    /**
     * The first cell along a line that stops a column, reading only loaded cells inside the build height.
     *
     * @param level  the level
     * @param from   the cell the line starts beside
     * @param toward the direction it runs
     * @param most   the most cells it runs
     * @return the cell that stops it, or null where it runs its whole length, out of the world or into unloaded cells
     */
    public static @Nullable BlockPos stoppedAt(Level level, BlockPos from, Direction toward, int most) {
        BlockPos.MutableBlockPos cell = from.mutable();
        for (int step = 1; step <= most; step++) {
            cell.move(toward);
            if (level.isOutsideBuildHeight(cell) || !level.isLoaded(cell)) {
                return null;
            }
            if (!passes(level.getBlockState(cell))) {
                return cell.immutable();
            }
        }
        return null;
    }

    /**
     * Whether a column runs through a block: air, or the light rail a reflector lays.
     *
     * @param state the block
     * @return true where the column runs on
     */
    static boolean passes(BlockState state) {
        return state.isAir() || state.getBlock() instanceof LightRailBlock;
    }
}
