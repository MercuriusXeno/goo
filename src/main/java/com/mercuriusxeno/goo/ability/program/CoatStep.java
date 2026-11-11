package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Weird Goop's block step, run each held tick of the stream: the slime
 * paints the open cell over every block it reaches whose top can hold a
 * coat, and every cell the hold has painted builds toward a slime coat while
 * the hold lasts, whether or not the aim is still on it, coating once it has
 * built the JSON's ticks. A painted cell past nine tenths of its coat is left to
 * finish alone, even after the hold ends, and a cell the hold has coated
 * once it coats no more in that hold. A cell already holding slime takes
 * one layer more, once per hold. The block under the coat is left as it stood.
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * slime-is-a-layered-cell-that-levels-out
 * decay-gnats-degrade-each-block-once
 *
 * <p>On a splat it lays the JSON's layers in the landing cell at once, and
 * the flow spreads what stands above level into the cells beside it
 * (decision bounce-throw-dumps-layers-that-flow-out). A step names ticks
 * for a stream's hold or layers for a splat, never both.
 *
 * @param ticks  the ticks of stream a cell takes to coat, 0 on a splat
 * @param layers the coat layers a splat lays in its landing cell, 0 on a stream
 */
public record CoatStep(int ticks, int layers) implements TakesNoEntity {

    private static final String NAME = "coat";
    private static final String FIELD_TICKS = "ticks";
    private static final String FIELD_LAYERS = "layers";
    /** Marks the delivery a step does not name. */
    private static final int UNNAMED = 0;
    private static final String ERR_ONE_DELIVERY = "a coat step names ticks for a stream or layers for a splat,"
            + " exactly one of them";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<CoatStep> CODEC = RecordCodecBuilder.<CoatStep>mapCodec(inst -> inst.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf(FIELD_TICKS, UNNAMED).forGetter(CoatStep::ticks),
            Codec.intRange(1, SlimeCoatBlock.MAX_LAYERS).optionalFieldOf(FIELD_LAYERS, UNNAMED)
                    .forGetter(CoatStep::layers)
    ).apply(inst, CoatStep::new)).validate(CoatStep::namesOneDelivery);

    /**
     * The registered type.
     */
    public static final StepType<CoatStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A stream's coat step.
     *
     * @param ticks the ticks of stream a cell takes to coat
     * @return the step
     */
    public static CoatStep streamed(int ticks) {
        return new CoatStep(ticks, UNNAMED);
    }

    /**
     * A splat's coat step.
     *
     * @param layers the coat layers it lays in its landing cell
     * @return the step
     */
    public static CoatStep splatted(int layers) {
        return new CoatStep(UNNAMED, layers);
    }

    private static DataResult<CoatStep> namesOneDelivery(CoatStep step) {
        return (step.ticks == UNNAMED) == (step.layers == UNNAMED) ? DataResult.error(() -> ERR_ONE_DELIVERY)
                : DataResult.success(step);
    }

    /**
     * Lays coat layers of slime in a cell, on top of any slime standing
     * there, up to a whole block; a cell holding anything a placed block
     * would not replace takes none. The cell's flow then pours what stands
     * above level into its neighbours.
     * bounce-throw-dumps-layers-that-flow-out
     *
     * @param level  the level
     * @param cell   the cell
     * @param layers the coat layers to lay
     */
    static void laySlimeIn(Level level, BlockPos cell, int layers) {
        BlockState standing = level.getBlockState(cell);
        int held = SlimeCoatBlock.layersOf(standing);
        if (held == 0 && !standing.canBeReplaced()) {
            return;
        }
        BlockState slime = held > 0 ? standing : GooBlocks.SLIME_COAT.get().defaultBlockState();
        level.setBlock(cell, slime.setValue(SlimeCoatBlock.LAYERS, Math.min(SlimeCoatBlock.MAX_LAYERS,
                held + layers)), Block.UPDATE_ALL);
    }

    @Override
    public StepType<CoatStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (layers != UNNAMED) {
            // bounce-throw-dumps-layers-that-flow-out
            context.hostAs(PlaceBlockHost.class).laySlime(layers);
            return true;
        }
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                paintOpenTops(host, PaintedHold.seenReach(host, aim), marks);
            }
            // the coat mingles in wearing its own green
            PaintedHold.buildPaintedStates(host, CoatStep::coatOver, CoatStep::sameLayers, marks, 1f / ticks,
                    raw -> OptionalInt.empty(), (pos, raw, into) -> host.transformBlock(pos, into));
        });
        return true;
    }

    /**
     * Paints the open cell over each reached block whose top can hold a
     * coat, and each reached cell already holding slime.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param host  the channel host
     * @param seen  the cells the stream's rays reached
     * @param marks the hold's marks
     */
    private static void paintOpenTops(ChannelHost host, Collection<BlockPos> seen, HoldMarks marks) {
        for (BlockPos pos : seen) {
            BlockPos over = pos.above();
            if (SlimeCoatBlock.layersOf(host.stateAt(pos)) > 0) {
                marks.paint(pos, host.stateAt(pos));
            }
            if (!host.airAt(pos) && host.airAt(over) && SlimeCoatBlock.liesOn(host.level(), over)) {
                marks.paint(over, host.stateAt(over));
            }
        }
    }

    /**
     * What a painted cell becomes: an open cell takes a coat of one layer,
     * a slime cell one layer more, anything else nothing.
     * slime-is-a-layered-cell-that-levels-out
     *
     * @param standing the state the cell held when painted
     * @return the thickened state, empty where the cell takes no layer
     */
    private static Optional<BlockState> coatOver(BlockState standing) {
        return SlimeCoatBlock.thickened(standing, GooBlocks.SLIME_COAT.get());
    }

    /**
     * Whether a painted cell still stands as painted: the same block holding
     * the same layers, so a slime cell that thickened alone is built no
     * further while its drips may change beside a new coat.
     *
     * @param now the cell's state now
     * @param raw the state it held when painted
     * @return true while the hold builds on it
     */
    private static boolean sameLayers(BlockState now, BlockState raw) {
        return now.getBlock() == raw.getBlock() && SlimeCoatBlock.layersOf(now) == SlimeCoatBlock.layersOf(raw);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(layers == UNNAMED ? HostCapability.CHANNEL : HostCapability.PLACE_BLOCK);
    }
}
