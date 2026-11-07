package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.ability.SlimeCoatBlock;
import com.mercuriusxeno.goo.network.HoldMarks;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import java.util.Collection;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Weird Coat's block step, run each held tick of the stream: the slime
 * paints the open cell over every block it reaches whose top can hold a
 * coat, and every cell the hold has painted builds toward a slime coat while
 * the hold lasts, whether or not the aim is still on it, coating once it has
 * built the JSON's ticks. A painted cell past nine tenths of its coat is left to
 * finish alone, even after the hold ends, and a cell the hold has coated
 * once it coats no more in that hold. The block under the coat is left as it stood.
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * decay-gnats-degrade-each-block-once
 *
 * @param ticks the ticks of stream a cell takes to coat
 */
public record CoatStep(int ticks) implements Step {

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

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            HoldMarks marks = host.holdMarks();
            if (aim.reaching()) {
                paintOpenTops(host, PaintedHold.seenReach(host, aim), marks);
            }
            // the coat mingles in wearing its own green
            PaintedHold.buildPainted(host, CoatStep::coatOver, marks, 1f / ticks, OptionalInt.empty());
        });
        return true;
    }

    /**
     * Paints the open cell over each reached block whose top can hold a coat.
     *
     * @param host  the channel host
     * @param seen  the cells the stream's rays reached
     * @param marks the hold's marks
     */
    private static void paintOpenTops(ChannelHost host, Collection<BlockPos> seen, HoldMarks marks) {
        for (BlockPos pos : seen) {
            BlockPos over = pos.above();
            if (!host.airAt(pos) && host.airAt(over) && SlimeCoatBlock.liesOn(host.level(), over)) {
                marks.paint(over, host.blockAt(over));
            }
        }
    }

    /**
     * The block a painted cell becomes: an open cell takes a slime coat, anything else nothing.
     *
     * @param standing the block the cell held when painted
     * @return the slime coat over an open cell, empty otherwise
     */
    private static Optional<Block> coatOver(Block standing) {
        return standing.defaultBlockState().isAir() ? Optional.of(GooBlocks.SLIME_COAT.get()) : Optional.empty();
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
