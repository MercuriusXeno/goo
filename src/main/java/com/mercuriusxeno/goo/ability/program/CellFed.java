package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SideCoat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Optional;

/**
 * What a drip's strike feeds: a cell, and the side whose wall the layer
 * coats there and how, a film clinging or a runny layer running down, or no
 * side where the layer joins the cell's fill.
 * drip-striking-a-side-sticks-or-runs-by-adhesion
 * goo-cell-holds-a-fill-and-a-state-per-side
 *
 * @param cell the cell fed
 * @param side the side whose wall the layer coats, empty for the fill
 * @param coat what the layer stands as on that side, bare for the fill
 */
public record CellFed(BlockPos cell, Optional<Direction> side, SideCoat coat) {

    /**
     * @param cell the cell whose fill takes the layer
     * @return the layer feeding that cell's fill
     */
    public static CellFed fill(BlockPos cell) {
        return new CellFed(cell, Optional.empty(), SideCoat.BARE);
    }

    /**
     * @param cell the cell holding the film
     * @param side the side whose wall the film clings to
     * @return the layer coating that wall as a film
     */
    public static CellFed film(BlockPos cell, Direction side) {
        return new CellFed(cell, Optional.of(side), SideCoat.FILM);
    }

    /**
     * @param cell the cell the layer strikes in
     * @param side the side whose wall it runs down
     * @return the layer running down that wall
     */
    public static CellFed runny(BlockPos cell, Direction side) {
        return new CellFed(cell, Optional.of(side), SideCoat.RUNNY);
    }
}
