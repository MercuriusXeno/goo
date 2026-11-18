package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Astral Dowsing's block step: Astral's Dowsing goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Slick its slick. A drip still in
 * flight when the hold ends lands its layer when it strikes.
 * astral-world-goo-is-dowsing-an-x-ray-of-ore
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record DowseStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "dowse";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<DowseStep> CODEC = MapCodec.unit(DowseStep::new);

    /**
     * The registered type.
     */
    public static final StepType<DowseStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DowseStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.ASTRAL_DOWSING.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.ASTRAL_DOWSING.get());
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
