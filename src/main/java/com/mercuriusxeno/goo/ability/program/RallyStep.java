package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zoo.Rallied;
import com.mercuriusxeno.goo.ability.zoo.RallyEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rallies the host's target for the caster and finishes: until the rally
 * fades, the mob strikes the caster's enemies for the named damage, its max
 * health and speed lifted by the named shares. A cast with no caster behind
 * it rallies nothing. Zoo's Rally is
 * {@code rally duration=600 damage=6 health=1.5 speed=0.5}.
 * zoo-rally-arms-the-peaceful
 *
 * @param duration the ticks the rally lasts, evaluated when the step runs
 * @param damage   the damage one strike deals
 * @param health   the share max health grows by
 * @param speed    the share movement speed grows by
 */
public record RallyStep(Expr duration, Expr damage, Expr health, Expr speed) implements Step {

    private static final String NAME = "rally";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<RallyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf("duration").forGetter(RallyStep::duration),
            Expr.CODEC.fieldOf("damage").forGetter(RallyStep::damage),
            Expr.CODEC.fieldOf("health").forGetter(RallyStep::health),
            Expr.CODEC.fieldOf("speed").forGetter(RallyStep::speed)
    ).apply(inst, RallyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RallyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RallyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        Entity caster = host.thrower();
        if (caster != null && host.target() instanceof Mob mob) {
            long expiresAt = mob.level().getGameTime() + duration.evaluateInt(context);
            mob.setData(GooAttachments.RALLIED, new Rallied(caster.getUUID(), expiresAt, damage.evaluateFloat(context)));
            RallyEvents.buff(mob, health.evaluate(context), speed.evaluate(context));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(duration, damage, health, speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
