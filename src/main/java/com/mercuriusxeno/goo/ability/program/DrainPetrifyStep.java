package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.PetrifyEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly Heal's mob step, run on each mob its stream holds each held tick:
 * the mob's petrify gauge drains by the JSON's amount, faster than the fog's
 * own drain, and the mob's speed follows it back up. A mob holding no gauge
 * is left alone.
 * decision heal-stream-drains-the-petrify-gauge
 *
 * @param amount how much the gauge drains each tick, evaluated on the mob
 */
public record DrainPetrifyStep(Expr amount) implements Step {

    private static final String NAME = "drain_petrify";
    private static final String FIELD_AMOUNT = "amount";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<DrainPetrifyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_AMOUNT).forGetter(DrainPetrifyStep::amount)
    ).apply(inst, DrainPetrifyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<DrainPetrifyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DrainPetrifyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (takesEntity(target) && !target.level().isClientSide() && target.hasData(GooAttachments.PETRIFICATION)) {
            Mob mob = (Mob) target;
            Petrification after = mob.getData(GooAttachments.PETRIFICATION)
                    .drainedBy((float) amount.evaluate(context));
            mob.setData(GooAttachments.PETRIFICATION, after);
            PetrifyEvents.slowBy(mob, after);
        }
        return true;
    }

    /**
     * Draining the petrify gauge takes a mob.
     * decision steps-declare-the-entities-they-take
     *
     * @param entity the candidate
     * @return true where the step acts on it
     */
    @Override
    public boolean takesEntity(Entity entity) {
        return entity instanceof Mob;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(amount);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
