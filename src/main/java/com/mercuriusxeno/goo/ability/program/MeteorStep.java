package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.world.MeteorSky;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Calls a meteor down where the blob lands: it falls from high above, from
 * rest and gathering speed, over its fall time, and explodes at the landing
 * with the named power. A target with no clear path to the sky refuses the
 * throw before it is paid for. Meteo is {@code meteor power=4 fall_ticks=40}.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 * decision meteo-needs-a-clear-sky
 *
 * @param power     the explosion's power, evaluated when the step runs
 * @param fallTicks the ticks the meteor falls for
 */
public record MeteorStep(Expr power, int fallTicks) implements TargetGatedStep {

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

    @Override
    public boolean admitsTarget(ServerLevel level, BlockPos cell) {
        return MeteorSky.clearAbove(level, cell);
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
