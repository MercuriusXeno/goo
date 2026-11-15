package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Calls a meteor down toward where the blob lands: it falls from high above,
 * from rest and gathering speed, over its fall time, and explodes with the
 * named power on the first block in its path, the cover over a covered
 * landing. Meteo is {@code meteor power=4 fall_ticks=20}, a one second fall
 * (decision meteor-lands-one-second-after-release).
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 * decision meteo-crashes-through-whatever-is-above
 *
 * @param power     the explosion's power, evaluated when the step runs
 * @param fallTicks the ticks the meteor falls for
 */
public record MeteorStep(Expr power, int fallTicks) implements Step {

    private static final String NAME = "meteor";
    private static final String FIELD_POWER = "power";
    private static final String FIELD_FALL_TICKS = "fall_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MeteorStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_POWER).forGetter(MeteorStep::power),
            Codec.INT.fieldOf(FIELD_FALL_TICKS).forGetter(MeteorStep::fallTicks)
    ).apply(inst, MeteorStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MeteorStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<MeteorStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(MeteorHost.class).callMeteor(power.evaluateFloat(context), fallTicks);
        return true;
    }

    /**
     * The radius the meteor's explosion cuts at most under these variables,
     * the open-air reach of its power, the blast zone drawn while Meteo charges.
     * decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob
     *
     * @param variables the host's variables
     * @return the reach in blocks
     */
    public double reachAt(Variables variables) {
        return ExplosionMarch.maxReach(power.evaluateFloat(variables));
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(power);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.METEOR);
    }
}
