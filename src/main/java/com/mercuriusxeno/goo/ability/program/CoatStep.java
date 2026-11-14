package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Weird Goop's block step: the slime lands where each of the stream's drips
 * strikes, one layer for each drip, in the tick it strikes. A drip striking
 * a block's top stands a one-layer cell in the open cell over it, where that
 * top can hold a coat, and a drip striking a cell short of fifteen layers
 * adds a layer to it; a full cell, or a drip striking a side, takes nothing.
 * Each layer pays a fifteenth of Goop's cost as it lands, and a layer the
 * player cannot pay for does not land. A drip still in flight when the hold
 * ends lands its layer when it strikes. The block under the slime is left as
 * it stood.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * goop-drains-per-cubic-meter-of-slime-placed
 * goop-layers-a-reached-cell-again-while-held
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 * bounce-leaves-the-mod-and-goop-stands-alone
 */
public record CoatStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "coat";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<CoatStep> CODEC = MapCodec.unit(CoatStep::new);

    /**
     * The registered type.
     */
    public static final StepType<CoatStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CoatStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the slime as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    /**
     * What the coat reads of the cells a strike may feed.
     */
    interface CoatCells {

        /**
         * @param pos a cell
         * @return true where the cell holds slime short of a whole block
         */
        boolean thickens(BlockPos pos);

        /**
         * @param pos a cell
         * @return true where the cell stands open over a top that can hold a coat
         */
        boolean takesACoat(BlockPos pos);
    }

    @Override
    public Optional<BlockPos> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return cellFedBy(strike, new CoatCells() {
            @Override
            public boolean thickens(BlockPos pos) {
                int layers = SlimeCoatBlock.layersOf(host.stateAt(pos));
                return layers > 0 && layers < SlimeCoatBlock.MAX_LAYERS;
            }

            @Override
            public boolean takesACoat(BlockPos pos) {
                return host.airAt(pos) && SlimeCoatBlock.liesOn(host.level(), pos);
            }
        });
    }

    /**
     * The cell a drip's strike feeds: slime short of a whole block struck on
     * its top, else the open cell over a struck top that can hold a coat; a
     * strike on a block's side or bottom feeds nothing.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     *
     * @param strike where the drip struck
     * @param cells  what the coat reads of the cells
     * @return the cell, empty where the strike lays nothing
     */
    static Optional<BlockPos> cellFedBy(CoatRay.BlockStrike strike, CoatCells cells) {
        // the operator's ruling: a coat lies only on a block's top, so a struck side lays nothing and slides nowhere
        if (strike.face() != Direction.UP) {
            return Optional.empty();
        }
        BlockPos struck = strike.block();
        if (cells.thickens(struck)) {
            return Optional.of(struck);
        }
        BlockPos over = struck.above();
        return cells.takesACoat(over) ? Optional.of(over) : Optional.empty();
    }

    /**
     * Lands the drip's layer in its cell once the player pays the layer's
     * price, a fifteenth of Goop's cost; a layer the player cannot pay for
     * does not land.
     * drip-creates-a-volume-cell-immediately
     * drip-on-a-standing-cell-increases-it
     * goop-drains-per-cubic-meter-of-slime-placed
     */
    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        coatOver(host.stateAt(cell)).filter(into -> host.placementPrice().pay(SlimeCoatBlock.MAX_LAYERS))
                .ifPresent(into -> host.transformBlock(cell, into));
    }

    /**
     * What a fed cell becomes: an open cell a cell of one layer, a slime
     * cell one layer more, anything else nothing.
     *
     * @param standing the state the cell holds
     * @return the thickened state, empty where the cell takes no layer
     */
    private static Optional<BlockState> coatOver(BlockState standing) {
        return SlimeCoatBlock.thickened(standing, GooBlocks.SLIME_COAT.get());
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
