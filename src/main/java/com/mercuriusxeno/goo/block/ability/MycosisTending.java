package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.reap.Reaping;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

/**
 * What a Mycosis cell tends in its reach on each of its growings: every
 * nether wart ages one stage, and each standing at its full age is reaped,
 * dropping its loot settled against the wart it is replanted from and
 * standing again at age 0; every placed fungal bud ripens one age, a ripe bud
 * growing into its mushroom on its own rule. The reach spans the cell's
 * reach across and one block up and down.
 * mycosis-grows-and-reaps-nether-wart
 */
public final class MycosisTending {

    /** The blocks up and down a cell's reach spans. */
    private static final int REACH_UP_AND_DOWN = 1;

    private MycosisTending() {
    }

    /**
     * The blocks around a cell within its reach, across and one up or down, the cell itself aside.
     *
     * @param cell  the cell
     * @param reach the blocks across it reaches
     * @return the blocks in reach
     */
    public static List<BlockPos> inReach(BlockPos cell, int reach) {
        List<BlockPos> around = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-reach, -REACH_UP_AND_DOWN, -reach),
                cell.offset(reach, REACH_UP_AND_DOWN, reach))) {
            if (!pos.equals(cell)) {
                around.add(pos.immutable());
            }
        }
        return around;
    }

    /**
     * Tends every nether wart and fungal bud in a cell's reach.
     *
     * @param level the server level
     * @param cell  the cell
     * @param reach the blocks across it reaches
     */
    public static void tendAround(ServerLevel level, BlockPos cell, int reach) {
        for (BlockPos pos : inReach(cell, reach)) {
            tend(level, pos);
        }
    }

    /**
     * Ages or reaps the fungus in a block; a block holding none is left as it is.
     *
     * @param level the server level
     * @param pos   the block
     */
    private static void tend(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof NetherWartBlock) {
            int age = state.getValue(NetherWartBlock.AGE);
            if (ripeWart(age)) {
                Reaping.replant(level, pos, state, state.setValue(NetherWartBlock.AGE, 0));
            } else {
                level.setBlock(pos, state.setValue(NetherWartBlock.AGE, age + 1), Block.UPDATE_CLIENTS);
            }
        } else if (state.getBlock() instanceof FungalBudBlock) {
            state.randomTick(level, pos, level.getRandom());
        }
    }

    /**
     * Whether a nether wart stands at its full age, ripe for the reaping.
     *
     * @param age the wart's age
     * @return true for a ripe wart
     */
    static boolean ripeWart(int age) {
        return age >= NetherWartBlock.MAX_AGE;
    }
}
