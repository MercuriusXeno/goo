package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * Which Nitro cells a detonating cell's blast sets off next: every other
 * Nitro cell whose center lies within the blast's reach of the detonating
 * cell's center, nearest first, so a field of it goes off outward.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 */
public final class NitroChain {

    private NitroChain() {
    }

    /**
     * The cells the detonating cell's blast sets off.
     *
     * @param detonating the cell going off
     * @param reach      the blast's reach, in blocks
     * @param nitroCells the Nitro cells standing around it
     * @return the cells set off next, nearest first
     */
    public static List<BlockPos> setOffNext(BlockPos detonating, double reach, Collection<BlockPos> nitroCells) {
        double reachSquared = reach * reach;
        return nitroCells.stream().filter(cell -> !cell.equals(detonating))
                .filter(cell -> cell.distSqr(detonating) <= reachSquared)
                .sorted(Comparator.comparingDouble(cell -> cell.distSqr(detonating))).toList();
    }
}
