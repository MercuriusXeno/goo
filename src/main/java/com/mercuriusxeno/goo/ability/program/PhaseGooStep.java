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
 * landing, as Leech lays Hex's goo. A thing standing in the goo, or hovering
 * in the zone over its top, a jump's height and some, is out of phase, as
 * Updraft's glide zone stands over its lift; the phase expires when it steps
 * off: {@code phase_goo zone_height=2}.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 * each-goo-type-ships-a-world-goo-on-the-volume-cell
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param zoneHeight how far over the goo's top a thing's feet stay out of phase, in blocks
 */
public record PhaseGooStep(double zoneHeight) implements TakesNoEntity, DripLanding {

    private static final String NAME = "phase_goo";
    private static final String FIELD_ZONE_HEIGHT = "zone_height";
    /** The tallest zone a cell holds over its top, a full cell's worth of blocks. */
    public static final double MOST_ZONE_HEIGHT = 16;

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PhaseGooStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.doubleRange(0, MOST_ZONE_HEIGHT).fieldOf(FIELD_ZONE_HEIGHT).forGetter(PhaseGooStep::zoneHeight)
    ).apply(inst, PhaseGooStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PhaseGooStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Whether the goo holds a thing out of phase: its feet in the goo, or in
     * the zone over the goo's top.
     * phase-goo-puts-what-stands-in-it-out-of-phase
     *
     * @param feetOverGooTop how far the thing's feet stand over the goo's top, below it negative
     * @return true where the goo holds it
     */
    public boolean holds(double feetOverGooTop) {
        return feetOverGooTop <= zoneHeight;
    }

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
