package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether's Infest block step: nether goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Goop lays its slime and Leech its hex goo: {@code infest}.
 * infest-is-nethers-goo-the-decay-effect-localized-around-the-goo
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record InfestStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "infest";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<InfestStep> CODEC = MapCodec.unit(new InfestStep());

    /**
     * The registered type.
     */
    public static final StepType<InfestStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The infest step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the infest step, empty where the program lays no Infest goo
     */
    public static Optional<InfestStep> in(List<Step> program) {
        return program.stream().filter(InfestStep.class::isInstance).map(InfestStep.class::cast).findFirst();
    }

    @Override
    public StepType<InfestStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Infest goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.NETHER_INFEST.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.NETHER_INFEST.get());
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
