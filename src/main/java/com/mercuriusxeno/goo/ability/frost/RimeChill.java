package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.ability.program.RimeStep;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A Rime cell's pass over its reach: the water and lava type sources in it
 * freeze, water to Rime ice and each lava type to what the molten groups
 * pair it with, nearest first, each
 * spending the layers Rime's step prices a freeze at, for as many as the
 * cell's layers pay for; what freezes stays frozen for good, kept by no
 * record, so nothing turns it back once the Rime is gone.
 * rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless
 * rime-freezing-is-permanent-with-no-restore-on-removal
 * rime-goo-depletes-on-freezing
 */
public final class RimeChill {

    private RimeChill() {
    }

    /**
     * What a pass's layers pay for.
     *
     * @param frozen     the sources the pass freezes, nearest the cell first
     * @param layersLeft the layers the cell holds after paying for them
     */
    public record Paid(List<BlockPos> frozen, int layersLeft) {
    }

    /**
     * The sources a cell's layers pay to freeze, nearest the cell first, at a
     * price in layers each, stopping at the last one the layers cover.
     *
     * @param cell    the Rime cell
     * @param layers  the layers the cell holds
     * @param price   the layers each freeze spends, one or more
     * @param sources the water and lava sources in the cell's reach, in any order
     * @return the sources frozen and the layers left
     */
    static Paid paidFor(BlockPos cell, int layers, int price, List<BlockPos> sources) {
        List<BlockPos> frozen = sources.stream().sorted(Comparator.comparingDouble(cell::distSqr))
                .limit(Math.min(sources.size(), layers / price)).toList();
        return new Paid(frozen, layers - frozen.size() * price);
    }

    /**
     * Freezes the water and lava sources within a Rime cell's reach that its
     * layers pay for; a reach running into an unloaded chunk freezes nothing
     * this pass.
     *
     * @param level  the server level
     * @param cell   the Rime cell
     * @param layers the layers the cell holds
     * @param rime   Rime's step, naming the reach and the layers a freeze spends
     * @return the layers the cell holds after the pass, 0 where it spent them all
     */
    public static int freezeAround(ServerLevel level, BlockPos cell, int layers, RimeStep rime) {
        int reach = rime.reach();
        if (!reachLoaded(level, cell, reach)) {
            return layers;
        }
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(cell.offset(-reach, -reach, -reach),
                cell.offset(reach, reach, reach))) {
            if (RimeFreeze.frozenForm(level.getBlockState(pos)).isPresent()) {
                sources.add(pos.immutable());
            }
        }
        Paid paid = paidFor(cell, layers, rime.layersPerFreeze(), sources);
        for (BlockPos pos : paid.frozen()) {
            RimeFreeze.frozenForm(level.getBlockState(pos))
                    .ifPresent(frozen -> level.setBlock(pos, frozen, Block.UPDATE_ALL));
        }
        return paid.layersLeft();
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
