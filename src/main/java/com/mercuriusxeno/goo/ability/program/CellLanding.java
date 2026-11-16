package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

/**
 * How a spray drip lands its goo on the volume cell, the one landing every
 * stream laying a world goo runs: a drip striking a block's top stands a
 * one-layer cell in the open cell over it, where that top can hold a world
 * goo, and a drip striking a cell of its goo short of fifteen layers adds a
 * layer to it, in the tick it strikes; a drip of a goo holding a side where
 * it strikes, adhesion 3, striking a block's side coats that wall with a
 * film in the open cell beside it; a full cell, a cell of another goo, or a
 * wall already coated takes nothing. Each layer pays a fifteenth of the
 * laying ability's cost as it lands, and a layer the player cannot pay for
 * does not land.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * drip-striking-a-side-sticks-or-runs-by-adhesion
 * adhesion-is-a-ladder-of-holds
 */
final class CellLanding {

    private CellLanding() {
    }

    /**
     * What the landing reads of the cells a strike may feed.
     */
    interface Cells {

        /**
         * @param pos a cell
         * @return true where the cell holds this goo short of a whole block
         */
        boolean thickens(BlockPos pos);

        /**
         * @param pos a cell
         * @return true where the cell stands open over a top that can hold a world goo
         */
        boolean takesACell(BlockPos pos);

        /**
         * @param cell a cell beside a struck wall
         * @param side the cell's side facing that wall
         * @return true where the cell is open or holds this goo with that side bare, and the wall holds a film
         */
        boolean coatsSide(BlockPos cell, Direction side);
    }

    /**
     * The cell a drip's strike feeds with a goo, read from the caster's level.
     *
     * @param host   the caster's host
     * @param strike where the drip struck
     * @param cell   the goo's volume cell block
     * @return the cell and the side fed, empty where the strike lays nothing
     */
    static Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike, VolumeCellBlock cell) {
        return cellFedBy(strike, cell.adhesionAt(host.level()), new Cells() {
            @Override
            public boolean thickens(BlockPos pos) {
                BlockState standing = host.stateAt(pos);
                // a cell of films alone takes fill only over a top that can hold it
                boolean floored = VolumeCellBlock.layersOf(standing) > 0 || VolumeCellBlock.liesOn(host.level(), pos);
                return standing.is(cell) && VolumeCellBlock.layersOf(standing) < VolumeCellBlock.MAX_LAYERS && floored;
            }

            @Override
            public boolean takesACell(BlockPos pos) {
                return host.airAt(pos) && VolumeCellBlock.liesOn(host.level(), pos);
            }

            @Override
            public boolean coatsSide(BlockPos pos, Direction side) {
                return VolumeCellBlock.filmed(host.stateAt(pos), cell, side).isPresent()
                        && VolumeCellBlock.clingsTo(host.level(), pos, side);
            }
        });
    }

    /**
     * The cell a drip's strike feeds: a cell of the goo short of a whole
     * block struck on its top, else the open cell over a struck top that can
     * hold a world goo; a strike on a block's side at adhesion 3 coats the
     * side of the open cell beside the struck face that faces the struck
     * block; any other strike feeds nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param strike   where the drip struck
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cells    what the landing reads of the cells
     * @return the cell and the side fed, empty where the strike lays nothing
     */
    static Optional<CellFed> cellFedBy(CoatRay.BlockStrike strike, int adhesion, Cells cells) {
        if (strike.face().getAxis().isHorizontal()) {
            return sideFedBy(strike, adhesion, cells);
        }
        if (strike.face() != Direction.UP) {
            return Optional.empty();
        }
        BlockPos struck = strike.block();
        if (cells.thickens(struck)) {
            return Optional.of(CellFed.fill(struck));
        }
        BlockPos over = struck.above();
        return cells.takesACell(over) ? Optional.of(CellFed.fill(over)) : Optional.empty();
    }

    /**
     * The film a strike on a block's side lays: at adhesion 3, a film in the
     * open cell beside the struck face, on its side facing the struck block.
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     * adhesion-is-a-ladder-of-holds
     *
     * @param strike   where the drip struck a block's side
     * @param adhesion the rung of the adhesion ladder the goo clings at
     * @param cells    what the landing reads of the cells
     * @return the cell and the side fed, empty where the strike lays nothing
     */
    private static Optional<CellFed> sideFedBy(CoatRay.BlockStrike strike, int adhesion, Cells cells) {
        BlockPos beside = strike.block().relative(strike.face());
        Direction wall = strike.face().getOpposite();
        boolean holds = adhesion >= GooTypeDefinition.HOLDS_WHERE_IT_STRIKES && cells.coatsSide(beside, wall);
        return holds ? Optional.of(CellFed.film(beside, wall)) : Optional.empty();
    }

    /**
     * Lands the drip's layer in its cell once the player pays the layer's
     * price, a fifteenth of the laying ability's cost: a fill layer spreads
     * the cell at once into its side cells, and a film clings to its wall; a
     * layer the player cannot pay for does not land.
     * drip-creates-a-volume-cell-immediately
     * drip-on-a-standing-cell-increases-it
     * spread-decreases-the-cell-it-leaves
     * goop-drains-per-cubic-meter-of-slime-placed
     * slick-drains-per-film-it-lays
     * drip-striking-a-side-sticks-or-runs-by-adhesion
     *
     * @param host the caster's host
     * @param fed  the cell and the side a strike fed
     * @param cell the goo's volume cell block
     */
    static void feed(ChannelHost host, CellFed fed, VolumeCellBlock cell) {
        BlockPos pos = fed.cell();
        BlockState standing = host.stateAt(pos);
        if (fed.side().isPresent()) {
            VolumeCellBlock.filmed(standing, cell, fed.side().get())
                    .filter(into -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                    .ifPresent(into -> host.transformBlock(pos, into));
            return;
        }
        VolumeCellBlock.thickened(standing, cell)
                .filter(into -> !VolumeCellBlock.wouldRiseAroundFeet(host.level(), pos,
                        VolumeCellBlock.layersOf(standing), into.getValue(VolumeCellBlock.LAYERS)))
                .filter(into -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                .ifPresent(into -> {
                    host.transformBlock(pos, into);
                    cell.spreadAtOnce(host.level(), pos);
                });
    }
}
