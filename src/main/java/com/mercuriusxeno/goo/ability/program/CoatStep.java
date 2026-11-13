package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Weird Goop's block step: the coat lands where each of the stream's drips
 * strikes. A drip striking a block's top feeds the open cell over it, where
 * that top can hold a coat, and a drip striking slime short of a whole block
 * feeds that slime; a drip striking a side feeds nothing. Each strike builds
 * its cell one share of the JSON's ticks toward a layer, so a cell drips
 * strike every tick coats in those ticks, and a cell drips keep striking
 * takes another layer each time they pass, until it is a whole block of
 * slime. A cell past nine tenths of its layer finishes alone. A drip still in
 * flight when the hold ends lands its share when it strikes, and each layer
 * pays its price as it lands. The block under the coat is left as it stood.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * goop-drains-per-cubic-meter-of-slime-placed
 * slime-is-a-layered-cell-that-levels-out
 * goop-layers-a-reached-cell-again-while-held
 * bounce-leaves-the-mod-and-goop-stands-alone
 *
 * @param ticks the ticks of strikes a cell takes to coat
 */
public record CoatStep(int ticks) implements TakesNoEntity, DripLanding {

    private static final String NAME = "coat";
    private static final String FIELD_TICKS = "ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CoatStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_TICKS).forGetter(CoatStep::ticks)
    ).apply(inst, CoatStep::new));

    /**
     * The registered type.
     */
    public static final StepType<CoatStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CoatStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the coat as they strike.
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

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        if (host.finishingAlone(cell)) {
            return;
        }
        coatOver(host.stateAt(cell)).ifPresent(into -> {
            PlacementPrice price = host.placementPrice();
            float share = 1f / ticks;
            // the coat mingles in wearing its own green
            float built = host.exposeBlock(cell, into, share);
            if (built >= 1f) {
                layPaidFor(host, price, cell, into);
            } else if (built > PaintedHold.FINISHES_ALONE) {
                host.finishBlockAlone(cell, share, () -> layPaidFor(host, price, cell, into));
            }
        });
    }

    /**
     * Lands a layer once the player pays its price, an eighth of a cubic
     * meter of slime, a strike's or a layer finishing alone alike; a layer
     * the player cannot pay for does not land.
     * goop-drains-per-cubic-meter-of-slime-placed
     *
     * @param host  the channel host
     * @param price what the layer pays as it lands
     * @param pos   the cell
     * @param into  the cell's state with the layer laid
     */
    private static void layPaidFor(ChannelHost host, PlacementPrice price, BlockPos pos, BlockState into) {
        if (price.pay(SlimeCoatBlock.MAX_LAYERS)) {
            host.transformBlock(pos, into);
        }
    }

    /**
     * What a fed cell becomes: an open cell takes a coat of one layer, a
     * slime cell one layer more, anything else nothing.
     * slime-is-a-layered-cell-that-levels-out
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
