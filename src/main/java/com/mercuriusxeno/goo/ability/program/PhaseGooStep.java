package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Quantum's Phase block step: Quantum's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Leech lays Hex's goo: {@code phase_goo}. The phase a thing
 * takes standing in the goo expires when it steps off.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 */
public record PhaseGooStep() implements TakesNoEntity, DripLanding {

    private static final String NAME = "phase_goo";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<PhaseGooStep> CODEC = MapCodec.unit(PhaseGooStep::new);

    /**
     * The registered type.
     */
    public static final StepType<PhaseGooStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The phase goo step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the phase goo step, empty where the program lays no Phase goo
     */
    public static Optional<PhaseGooStep> in(List<Step> program) {
        return program.stream().filter(PhaseGooStep.class::isInstance).map(PhaseGooStep.class::cast).findFirst();
    }

    @Override
    public StepType<PhaseGooStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the Phase goo as they strike.
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
        return CellLanding.cellFed(host, strike, GooBlocks.QUANTUM_PHASE.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.QUANTUM_PHASE.get());
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
