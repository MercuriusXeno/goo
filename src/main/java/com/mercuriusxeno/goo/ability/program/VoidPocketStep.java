package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Opens Void's pocket where the blob lands and finishes: a gate over the
 * struck surface partnered with a mirror on the caster's own room floor in
 * the pocket dimension, both changing back to the blocks they covered after
 * the lifetime while the room keeps what was built in it. Void's pocket is
 * {@code void_pocket lifetime=1200}.
 * decision void-opens-a-pocket-dimension
 *
 * @param lifetime the ticks the pair stands, evaluated when the step runs
 */
public record VoidPocketStep(Expr lifetime) implements Step {

    private static final String NAME = "void_pocket";
    private static final String FIELD_LIFETIME = "lifetime";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<VoidPocketStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_LIFETIME).forGetter(VoidPocketStep::lifetime)
    ).apply(inst, VoidPocketStep::new));

    /**
     * The registered type.
     */
    public static final StepType<VoidPocketStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<VoidPocketStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(GateHost.class).openVoidPocket(lifetime.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(lifetime);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.VOID_POCKET);
    }
}
