package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.Airborn;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target Airborn for this held tick of Float and a short
 * grace after it, then finishes: steering in midair and while levitating, a
 * capped fall without fall damage, higher jumps and a faster elytra glide.
 * Run every held tick of the channel, it stands while Float is held and ends
 * the grace after the hold lets go. Float's Airborn is {@code airborn
 * air_speed=0.35 air_steer=0.15 fall_cap=0.4 jump_boost=0.5 glide_speed=1.2 glide_steer=0.05}.
 * tailwind-and-airborn-merge-into-float
 *
 * @param airSpeed  the horizontal speed midair input drives toward, in blocks per tick
 * @param airSteer  the share of the way midair steering turns each tick
 * @param fallCap   the fastest the target falls, in blocks per tick
 * @param jumpBoost the share the target's jump strength grows by
 * @param glideSpeed the speed an elytra glide is drawn toward along the look, in blocks per tick
 * @param glideSteer the share of the way a slower glide is drawn toward it each tick
 */
public record AirbornStep(Expr airSpeed, Expr airSteer, Expr fallCap, Expr jumpBoost,
                          Expr glideSpeed, Expr glideSteer)
        implements Step {

    /**
     * The ticks Airborn outlasts the held tick that granted it, so a held
     * tick reaching the server a tick late leaves no gap in the air control.
     */
    public static final int HELD_TICK_GRACE = 5;

    private static final String NAME = "airborn";
    private static final String FIELD_AIR_SPEED = "air_speed";
    private static final String FIELD_AIR_STEER = "air_steer";
    private static final String FIELD_FALL_CAP = "fall_cap";
    private static final String FIELD_JUMP_BOOST = "jump_boost";
    private static final String FIELD_GLIDE_SPEED = "glide_speed";
    private static final String FIELD_GLIDE_STEER = "glide_steer";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<AirbornStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AIR_SPEED).forGetter(AirbornStep::airSpeed),
            Expr.CODEC.fieldOf(FIELD_AIR_STEER).forGetter(AirbornStep::airSteer),
            Expr.CODEC.fieldOf(FIELD_FALL_CAP).forGetter(AirbornStep::fallCap),
            Expr.CODEC.fieldOf(FIELD_JUMP_BOOST).forGetter(AirbornStep::jumpBoost),
            Expr.CODEC.fieldOf(FIELD_GLIDE_SPEED).forGetter(AirbornStep::glideSpeed),
            Expr.CODEC.fieldOf(FIELD_GLIDE_STEER).forGetter(AirbornStep::glideSteer)
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
        LivingEntity target = context.hostAs(TargetHost.class).target();
        target.setData(GooAttachments.AIRBORN, new Airborn(airSpeed.evaluateFloat(context),
                airSteer.evaluateFloat(context), fallCap.evaluateFloat(context), jumpBoost.evaluateFloat(context),
                glideSpeed.evaluateFloat(context), glideSteer.evaluateFloat(context),
                target.level().getGameTime() + HELD_TICK_GRACE));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(airSpeed, airSteer, fallCap, jumpBoost, glideSpeed, glideSteer);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
