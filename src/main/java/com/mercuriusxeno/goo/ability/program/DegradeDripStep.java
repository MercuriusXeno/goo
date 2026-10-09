package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Nether decay's tap step, run as each drip lands on the block below the
 * tap: each drip builds the block's exposure toward its degraded block in
 * the map the JSON names, the same map the channeled Decay reads, and the
 * drips the JSON names step it (decision decay-drip-degrades-the-block-below).
 *
 * @param map   the id of the block map the block steps along
 * @param drips the drips that step it
 */
public record DegradeDripStep(Identifier map, int drips) implements Step {

    private static final String NAME = "degrade_drip";
    private static final String FIELD_MAP = "map";
    private static final String FIELD_DRIPS = "drips";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DegradeDripStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_MAP).forGetter(DegradeDripStep::map),
            Codec.INT.fieldOf(FIELD_DRIPS).forGetter(DegradeDripStep::drips)
    ).apply(inst, DegradeDripStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DegradeDripStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DegradeDripStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        DripHost host = context.hostAs(DripHost.class);
        PetrifyDripStep.stepBelow(host, host.position(), map, drips);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.DRIP);
    }
}
