package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Crystal's Shatter block step: crystal goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Slick its slick, and glass shards
 * hang over every cell of it.
 * shatter-is-splattered-goo-and-shards-form-over-the-splatters
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record ShatterStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "shatter";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<ShatterStep> CODEC = MapCodec.unit(ShatterStep::new);

    /**
     * The registered type.
     */
    public static final StepType<ShatterStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShatterStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the crystal goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.CRYSTAL_SHATTER.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.CRYSTAL_SHATTER.get());
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
