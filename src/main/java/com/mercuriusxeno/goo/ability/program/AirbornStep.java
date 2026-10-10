package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target Airborn and finishes: steering in midair and
 * while levitating, a capped fall without fall damage, higher jumps and a
 * stronger Jet. Cast from the glove it stands as a held effect, paying its
 * upkeep until ended; drunk as a brew it stands for the brew's hour. Airborn
 * is {@code airborn air_speed=0.35 air_steer=0.15 fall_cap=0.4 jump_boost=0.5 jet_boost=1.5}.
 * airborn-steerable-levitation-and-soft-falls
 *
 * @param airSpeed  the horizontal speed midair input drives toward, in blocks per tick
 * @param airSteer  the share of the way midair steering turns each tick
 * @param fallCap   the fastest the target falls, in blocks per tick
 * @param jumpBoost the share the target's jump strength grows by
 * @param jetBoost  what Jet's push strength is multiplied by
 */
public record AirbornStep(Expr airSpeed, Expr airSteer, Expr fallCap, Expr jumpBoost, Expr jetBoost)
        implements Step {

    private static final String NAME = "airborn";
    private static final String FIELD_AIR_SPEED = "air_speed";
    private static final String FIELD_AIR_STEER = "air_steer";
    private static final String FIELD_FALL_CAP = "fall_cap";
    private static final String FIELD_JUMP_BOOST = "jump_boost";
    private static final String FIELD_JET_BOOST = "jet_boost";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AirbornStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AIR_SPEED).forGetter(AirbornStep::airSpeed),
            Expr.CODEC.fieldOf(FIELD_AIR_STEER).forGetter(AirbornStep::airSteer),
            Expr.CODEC.fieldOf(FIELD_FALL_CAP).forGetter(AirbornStep::fallCap),
            Expr.CODEC.fieldOf(FIELD_JUMP_BOOST).forGetter(AirbornStep::jumpBoost),
            Expr.CODEC.fieldOf(FIELD_JET_BOOST).forGetter(AirbornStep::jetBoost)
    ).apply(inst, AirbornStep::new));

    /**
     * The registered type.
     */
    public static final StepType<AirbornStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<AirbornStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        long now = target.level().getGameTime();
        OptionalInt brew = host.brewDuration();
        long standing = target.getData(GooAttachments.AIRBORN).expiresAt();
        long endsAt = brew.isPresent() ? Math.max(standing, now + brew.getAsInt()) : Airborn.NEVER_EXPIRES;
        target.setData(GooAttachments.AIRBORN, new Airborn(airSpeed.evaluateFloat(context),
                airSteer.evaluateFloat(context), fallCap.evaluateFloat(context), jumpBoost.evaluateFloat(context),
                jetBoost.evaluateFloat(context), endsAt));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(airSpeed, airSteer, fallCap, jumpBoost, jetBoost);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
