package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Durable's Coat block step: durable goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing. Runny and hanging, it spreads thin over tops, sides and
 * undersides and wraps what it is sprayed on; its cell resists blasts and
 * picks as obsidian does and keeps the block it stands against.
 * durable-goo-is-a-protective-coating-that-blocks-damage
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 */
public record CoatStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "coat";

    /**
     * Codec for the step, which carries no params.
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
     * Runs nothing on the hold's tick: the stream's drips lay the Coat goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.DURABLE_COAT.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.DURABLE_COAT.get());
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
