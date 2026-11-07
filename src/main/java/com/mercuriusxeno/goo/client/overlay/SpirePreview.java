package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.SpireFootprint;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

/**
 * Which cells Spire's preview draws: the ripple spans the footprint's ground
 * cells, the tops the stone grows from, and once the footprint is fixed the
 * ghost stone fills every cell the rise would fill above them.
 * decision spire-previews-as-a-surface-ripple-and-ghost-stone
 */
final class SpirePreview {

    private SpirePreview() {
    }

    /**
     * The cells the ripple spans: the footprint's ground cells, whose tops it shimmers over.
     *
     * @param footprint the live footprint
     * @return the ground cells
     */
    static List<BlockPos> rippleCells(SpireFootprint footprint) {
        return footprint.groundCells();
    }

    /**
     * The cells the ghost stone fills: above each ground cell, from the cell
     * on top of it up through the rise.
     *
     * @param footprint the footprint, at its rise
     * @return the cells, column by column
     */
    static List<BlockPos> ghostCells(SpireFootprint footprint) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos ground : footprint.groundCells()) {
            for (int height = 1; height <= footprint.rise(); height++) {
                cells.add(ground.above(height));
            }
        }
        return cells;
    }
}
