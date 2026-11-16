package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * A Rime cell's pass over its reach: each water source in it freezes to Rime
 * ice and each lava source to obsidian, for good, kept by no record, so
 * nothing turns them back once the Rime is gone.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * rime-freezing-is-permanent-with-no-restore-on-removal
 */
public final class RimeChill {

    private RimeChill() {
    }

    /**
     * Freezes the water and lava sources within a Rime cell's reach; a reach
     * running into an unloaded chunk freezes nothing this pass.
     *
     * @param level the server level
     * @param cell  the Rime cell
     * @param reach how far from the cell water and lava freeze, in blocks along each axis
     */
    public static void freezeAround(ServerLevel level, BlockPos cell, int reach) {
        if (!reachLoaded(level, cell, reach)) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-reach, -reach, -reach),
                cell.offset(reach, reach, reach))) {
            RimeFreeze.of(level.getBlockState(pos)).flatMap(RimeFreeze::freeze)
                    .ifPresent(frozen -> level.setBlock(pos, frozen.state(), Block.UPDATE_ALL));
        }
    }

    /**
     * Whether every chunk a reach around a block runs into is loaded, read
     * at the reach's four corners: a reach of at most RimeStep's longest
     * spans no more than two chunks along each axis, so its corners stand in
     * every chunk it touches.
     *
     * @param level the level
     * @param pos   the block at the reach's middle
     * @param reach how far the reach runs, in blocks along each axis
     * @return true where all of it is loaded
     */
    private static boolean reachLoaded(Level level, BlockPos pos, int reach) {
        int[] edges = {-reach, reach};
        for (int dx : edges) {
            for (int dz : edges) {
                if (!level.isLoaded(pos.offset(dx, 0, dz))) {
                    return false;
                }
            }
        }
        return true;
    }
}
