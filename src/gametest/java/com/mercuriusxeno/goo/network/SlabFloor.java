package com.mercuriusxeno.goo.network;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

/**
 * A floor of bottom slabs around a streamer's stand, whose tops hold no
 * world goo, so a layer dropping onto it finds no top to land on and is
 * neither laid nor paid for.
 * drip-striking-a-ceiling-runs-down-a-wall-or-drops
 */
final class SlabFloor {

    /** A stand clear of the bay's walls, so no drip striking a ceiling over it finds a wall to run down. */
    static final BlockPos CLEAR_STAND = new BlockPos(4, 1, 4);

    /** Blocks the floor reaches past the stand on every side, past the drops of a cone aimed straight up. */
    private static final int REACH = 2;

    private SlabFloor() {
    }

    /**
     * Lays the slab floor in place of the floor under a stand and around it.
     *
     * @param helper the gametest helper
     * @param stand  the cell the streamer stands in, relative to the test
     */
    static void layUnder(GameTestHelper helper, BlockPos stand) {
        for (int east = -REACH; east <= REACH; east++) {
            for (int south = -REACH; south <= REACH; south++) {
                helper.setBlock(stand.below().offset(east, 0, south), Blocks.STONE_SLAB);
            }
        }
    }
}
