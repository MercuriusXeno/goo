package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The souls Leech goo pops now and then, vanilla's soul particle rising from
 * the goo's surface: whether a cell's animate tick pops one, and where it
 * rises from.
 * soul-particles-pop-from-leech-goo-now-and-then
 */
public final class LeechSouls {

    private LeechSouls() {
    }

    /**
     * Whether a Leech cell's animate tick pops a soul.
     *
     * @param draw a random draw in [0, 1)
     * @param rate the chance a cell pops a soul each animate tick
     * @return true where the draw falls under the rate
     */
    static boolean pops(double draw, double rate) {
        return draw < rate;
    }

    /**
     * Where a popped soul rises from: a point on the goo's surface, at the
     * height the cell's layers reach, within its block's column.
     *
     * @param cell    the cell
     * @param layers  the layers it holds
     * @param acrossX where across the column, west to east, a draw in [0, 1)
     * @param acrossZ where across the column, north to south, a draw in [0, 1)
     * @return the rise point
     */
    static Vec3 risePoint(BlockPos cell, int layers, double acrossX, double acrossZ) {
        return Vec3.atLowerCornerOf(cell).add(acrossX, (double) layers / VolumeCellBlock.MAX_LAYERS, acrossZ);
    }
}
