package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Deep's Host block step: Deep's sculky slime lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Molten its molten goo:
 * {@code host}.
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record HostStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "host";

    /**
     * Codec for the step, which carries no params.
     */
    public static final MapCodec<HostStep> CODEC = MapCodec.unit(HostStep::new);

    /**
     * The registered type.
     */
    public static final StepType<HostStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<HostStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Host goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.DEEP_HOST.get());
    }

    @Override
    public void feed(ChannelHost host, BlockPos cell) {
        CellLanding.feed(host, cell, GooBlocks.DEEP_HOST.get());
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
