package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens a Dragon Gate where the blob lands and finishes: a three by three
 * end portal over the struck surface, partnered with a mirror on the End's
 * platform, both changing back to the blocks they covered after the
 * lifetime. Dragon Gate is {@code dragon_gate lifetime=1200}.
 * Decision end-clears-blocks-and-opens-a-portal.
 *
 * @param lifetime the ticks the pair stands, evaluated when the step runs
 */
public record DragonGateStep(Expr lifetime) implements Step {

    private static final String NAME = "dragon_gate";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DragonGateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(DragonGateStep::lifetime)
    ).apply(inst, DragonGateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DragonGateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DragonGateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(GateHost.class).openDragonGate(lifetime.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.DRAGON_GATE);
    }
}
