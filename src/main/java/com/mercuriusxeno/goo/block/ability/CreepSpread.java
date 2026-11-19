package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * The choices Creep goo makes about sculk: the charge a cursor carries on out
 * of a Creep cell, the layers the cell spends turning the block under it to
 * sculk, and the cell a death's charge starts at.
 * sculk-charge-runs-along-host-goo-without-weakening
 * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
 */
public final class CreepSpread {

    private CreepSpread() {
    }

    /**
     * The charge a cursor carries out of a Creep cell: all it carried in, where
     * on ordinary sculk it would decay, save that a charge stranded on the
     * goo, with nothing left to turn and no step off it, ends.
     *
     * @param chargeIn the charge the cursor carried into the cell
     * @param stranded whether no reachable cell turns its block and no step leads off the goo
     * @return the charge it carries on, 0 where it ends
     */
    public static int chargeOut(int chargeIn, boolean stranded) {
        return stranded ? 0 : chargeIn;
    }

    /**
     * The layers a Creep cell spends turning the block under it to sculk: a
     * block of sculk's worth where that block is ground sculk replaces and the
     * cell holds that many, none otherwise.
     *
     * @param layers       the layers the cell holds
     * @param turnsToSculk whether the block under the cell is ground sculk replaces
     * @param sculkLayers  the layers a block of sculk spends, the Creep step's tunable
     * @return the layers spent, 0 where the block under the cell stays
     */
    public static int layersSpent(int layers, boolean turnsToSculk, int sculkLayers) {
        return turnsToSculk && layers >= sculkLayers ? sculkLayers : 0;
    }

    /**
     * The Creep cell a death's charge starts at: the cell nearest the death,
     * where it lies within Creep's radius of it.
     * mob-dying-on-or-near-host-goo-spreads-sculk-from-the-goo
     *
     * @param death  where the mob died
     * @param cells  the Creep cells around the death
     * @param radius how far from the death a cell still counts as near it
     * @return the starting cell, empty where no cell lies near enough
     */
    public static Optional<BlockPos> startingCell(BlockPos death, Collection<BlockPos> cells, int radius) {
        double reach = (double) radius * radius;
        return cells.stream().filter(cell -> cell.distSqr(death) <= reach)
                .min(Comparator.comparingDouble(cell -> cell.distSqr(death)));
    }
}
