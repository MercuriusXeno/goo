package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Frost's Rime block step: Frost's endothermic goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Host its sculky slime:
 * {@code rime}.
 * iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record RimeStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "rime";

    /**
     * Codec for the step, which carries no params.
     */
    public static final MapCodec<RimeStep> CODEC = MapCodec.unit(RimeStep::new);

    /**
     * The registered type.
     */
    public static final StepType<RimeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RimeStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Rime goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.FROST_RIME.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.FROST_RIME.get());
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
