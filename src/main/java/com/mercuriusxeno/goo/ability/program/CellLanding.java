package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.throwing.CoatRay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;

/**
 * How a spray drip lands its goo on the volume cell, the one landing every
 * stream laying a world goo runs: a drip striking a block's top stands a
 * one-layer cell in the open cell over it, where that top can hold a world
 * goo, and a drip striking a cell of its goo short of fifteen layers adds a
 * layer to it, in the tick it strikes; a full cell, a cell of another goo, or
 * a drip striking a side takes nothing. Each layer pays a fifteenth of the
 * laying ability's cost as it lands, and a layer the player cannot pay for
 * does not land.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
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
    }

    /**
     * The cell a drip's strike feeds with a goo, read from the caster's level.
     *
     * @param host   the caster's host
     * @param strike where the drip struck
     * @param cell   the goo's volume cell block
     * @return the cell, empty where the strike lays nothing
     */
    static Optional<BlockPos> cellFed(ChannelHost host, CoatRay.BlockStrike strike, Block cell) {
        return cellFedBy(strike, new Cells() {
            @Override
            public boolean thickens(BlockPos pos) {
                BlockState standing = host.stateAt(pos);
                return standing.is(cell) && standing.getValue(VolumeCellBlock.LAYERS) < VolumeCellBlock.MAX_LAYERS;
            }

            @Override
            public boolean takesACell(BlockPos pos) {
                return host.airAt(pos) && VolumeCellBlock.liesOn(host.level(), pos);
            }
        });
    }

    /**
     * The cell a drip's strike feeds: a cell of the goo short of a whole
     * block struck on its top, else the open cell over a struck top that can
     * hold a world goo; a strike on a block's side or bottom feeds nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param strike where the drip struck
     * @param cells  what the landing reads of the cells
     * @return the cell, empty where the strike lays nothing
     */
    static Optional<BlockPos> cellFedBy(CoatRay.BlockStrike strike, Cells cells) {
        // the operator's ruling: a coat lies only on a block's top, so a struck side lays nothing and slides nowhere
        if (strike.face() != Direction.UP) {
            return Optional.empty();
        }
        BlockPos struck = strike.block();
        if (cells.thickens(struck)) {
            return Optional.of(struck);
        }
        BlockPos over = struck.above();
        return cells.takesACell(over) ? Optional.of(over) : Optional.empty();
    }

    /**
     * Lands the drip's layer in its cell once the player pays the layer's
     * price, a fifteenth of the laying ability's cost; a layer the player
     * cannot pay for does not land.
     * drip-creates-a-volume-cell-immediately
     * drip-on-a-standing-cell-increases-it
     * goop-drains-per-cubic-meter-of-slime-placed
     * slick-drains-per-film-it-lays
     *
     * @param host the caster's host
     * @param pos  the cell a strike fed
     * @param cell the goo's volume cell block
     */
    static void feed(ChannelHost host, BlockPos pos, Block cell) {
        VolumeCellBlock.thickened(host.stateAt(pos), cell)
                .filter(into -> host.placementPrice().pay(VolumeCellBlock.MAX_LAYERS))
                .ifPresent(into -> host.transformBlock(pos, into));
    }
}
