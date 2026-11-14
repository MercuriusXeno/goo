package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Weird Goop's block step: Weird's slime lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing. A drip still in flight when the hold ends lands its layer when it
 * strikes. The block under the slime is left as it stood.
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * coat-is-a-green-slime-film-over-the-blocks-own-texture
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 * goop-drains-per-cubic-meter-of-slime-placed
 * goop-layers-a-reached-cell-again-while-held
 * slime-and-slick-are-one-volume-cell-tech
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

    @Override
    public Optional<BlockPos> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.SLIME_COAT.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.SLIME_COAT.get());
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
