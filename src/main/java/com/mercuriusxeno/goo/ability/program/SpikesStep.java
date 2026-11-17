package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Metal's Spikes block step: metal goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo, so a held stream
 * lays a thick coat: {@code spikes}.
 * metal-world-goo-is-named-spikes
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record SpikesStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "spikes";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<SpikesStep> CODEC = MapCodec.unit(SpikesStep::new);

    /**
     * The registered type.
     */
    public static final StepType<SpikesStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The spikes step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the spikes step, empty where the program lays no Spikes goo
     */
    public static Optional<SpikesStep> in(List<Step> program) {
        return program.stream().filter(SpikesStep.class::isInstance).map(SpikesStep.class::cast).findFirst();
    }

    @Override
    public StepType<SpikesStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Spikes goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.METAL_SPIKES.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.METAL_SPIKES.get());
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
