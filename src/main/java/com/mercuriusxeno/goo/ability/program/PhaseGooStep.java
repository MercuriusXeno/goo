package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Quantum's Phase block step: Quantum's goo lands where each of the stream's
 * drips strikes, one layer for each drip, through the volume cell's one
 * landing, as Leech lays Hex's goo. The step names how long the phase a
 * thing took standing in the goo lingers once it steps off, in seconds for
 * each layer of the cell it left:
 * {@code phase_goo linger_seconds_per_layer=2}.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param lingerSecondsPerLayer the seconds the phase lingers after stepping off, for each layer of the cell left
 */
public record PhaseGooStep(double lingerSecondsPerLayer) implements TakesNoEntity, DripLanding {

    private static final String NAME = "phase_goo";
    private static final String FIELD_LINGER_SECONDS_PER_LAYER = "linger_seconds_per_layer";
    private static final int TICKS_A_SECOND = 20;
    /** The longest a layer lingers, a minute, so a full cell lingers a quarter hour at most. */
    private static final double MOST_LINGER_SECONDS_PER_LAYER = 60.0;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PhaseGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0.0, MOST_LINGER_SECONDS_PER_LAYER).fieldOf(FIELD_LINGER_SECONDS_PER_LAYER)
                    .forGetter(PhaseGooStep::lingerSecondsPerLayer)
    ).apply(inst, PhaseGooStep::new));

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

    /**
     * The ticks the phase lingers after its thing steps off a cell of some
     * layers: the layers times the seconds a layer, in ticks.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     *
     * @param layers the layers of the cell stepped off
     * @return the ticks the phase lingers
     */
    public int lingerTicks(int layers) {
        return (int) Math.round(layers * lingerSecondsPerLayer * TICKS_A_SECOND);
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
