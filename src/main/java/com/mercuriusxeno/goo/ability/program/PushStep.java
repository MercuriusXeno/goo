package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.typhoon.AirbornMotion;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Moves the host's target along the thrower's look and finishes. On the
 * player host the player is its own thrower, so it is pushed along its own
 * look (decision self-delivery-runs-on-player). Each run turns the target's
 * velocity a share of the way toward the look at the push's speed: a share of
 * one sets it outright, and a smaller share, run every held tick, steers it
 * round gradually, the elytra share taking over while the target wears an
 * elytra. Typhoon jet is {@code push strength=0.8 steer=0.2 elytra_steer=0.5}.
 * jet-pushes-along-the-look-while-held
 *
 * @param strength    the speed the push drives toward, in blocks per tick, evaluated when the step runs
 * @param steer       the share of the way the velocity turns toward the push each run, 0 to 1
 * @param elytraSteer the share it turns while the target wears an elytra, 0 to 1
 */
public record PushStep(Expr strength, Expr steer, Expr elytraSteer) implements Step {

    private static final String NAME = "push";
    private static final String FIELD_STRENGTH = "strength";
    private static final String FIELD_STEER = "steer";
    private static final String FIELD_ELYTRA_STEER = "elytra_steer";
    private static final Expr OUTRIGHT = Expr.literal(1);

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PushStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_STRENGTH).forGetter(PushStep::strength),
            Expr.CODEC.optionalFieldOf(FIELD_STEER, OUTRIGHT).forGetter(PushStep::steer),
            Expr.CODEC.optionalFieldOf(FIELD_ELYTRA_STEER, OUTRIGHT).forGetter(PushStep::elytraSteer)
    ).apply(inst, PushStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PushStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * A push that sets the target's velocity outright.
     *
     * @param strength the speed the push sets, in blocks per tick
     */
    public PushStep(Expr strength) {
        this(strength, OUTRIGHT, OUTRIGHT);
    }

    @Override
    public StepType<PushStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        Entity thrower = host.thrower();
        if (thrower != null) {
            LivingEntity target = host.target();
            double share = (wearsElytra(target) ? elytraSteer : steer).evaluate(context);
            // airborn-steerable-levitation-and-soft-falls: Airborn makes Jet's push stronger
            double speed = AirbornMotion.jetStrength(strength.evaluate(context),
                    target.getData(GooAttachments.AIRBORN), target.level().getGameTime());
            host.push(steered(target.getDeltaMovement(), thrower.getLookAngle().scale(speed), share));
        }
        return true;
    }

    /**
     * A velocity turned a share of the way toward the one the push drives at.
     *
     * @param current the velocity now
     * @param pushed  the velocity the push drives toward
     * @param share   the share of the way, clamped to 0 to 1
     * @return the turned velocity
     */
    static Vec3 steered(Vec3 current, Vec3 pushed, double share) {
        return current.lerp(pushed, Math.clamp(share, 0, 1));
    }

    private static boolean wearsElytra(LivingEntity target) {
        return target.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(strength, steer, elytraSteer);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
