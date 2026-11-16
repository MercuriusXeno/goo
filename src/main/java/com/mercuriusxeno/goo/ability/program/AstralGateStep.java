package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Astral's gate: fixes the caster's gaze on the moon or the blue overworld star their look holds, holding them
 * still while the view closes in, then carries them, and finishes; it places nothing. Astral's gate is
 * {@code astral_gate fixation=60 cost=1000}, three seconds of fixation.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision gate-on-the-blue-star-returns-you-exactly-where-you-left
 *
 * @param fixation the ticks the fixation runs before the gate carries the caster
 * @param cost     the astral the gate costs as the fixation begins (mB)
 */
public record AstralGateStep(Expr fixation, Expr cost) implements Step {

    private static final String NAME = "astral_gate";
    private static final String FIELD_FIXATION = "fixation";
    private static final String FIELD_COST = "cost";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AstralGateStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FIXATION).forGetter(AstralGateStep::fixation),
            Expr.CODEC.fieldOf(FIELD_COST).forGetter(AstralGateStep::cost)
    ).apply(inst, AstralGateStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AstralGateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AstralGateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(AstralGateHost.class).fixateOnTheSky(fixation.evaluateInt(context), cost.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fixation, cost);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.ASTRAL_GATE);
    }
}
