package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Unstable's Nitro block step: unstable goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume
 * cell's one landing, as Infest lays its nether goo: {@code nitro}.
 * nitro-is-unstables-goo-that-explodes-in-a-chain-by-its-quantity
 * lurker-blob-brightens-then-detonates
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record NitroStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "nitro";

    /**
     * Codec for the step, which names no params.
     */
    public static final MapCodec<NitroStep> CODEC = MapCodec.unit(NitroStep::new);

    /**
     * The registered type.
     */
    public static final StepType<NitroStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<NitroStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Nitro goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.UNSTABLE_NITRO.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.UNSTABLE_NITRO.get());
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
