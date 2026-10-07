package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.petrify.Petrification;
import com.mercuriusxeno.goo.ability.petrify.PetrifyEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rock petrify's mob step, run on each mob the stream holds each held tick:
 * the mob's petrify gauge fills by the JSON's amount, and the tick it fills
 * the mob becomes a statue. A target that is no mob is left alone.
 * decision petrify-stone-encasement-and-calcify-map
 *
 * @param fill how much the gauge fills each tick, evaluated on the mob
 */
public record PetrifyStep(Expr fill) implements Step {

    private static final String NAME = "petrify";
    private static final String FIELD_FILL = "fill";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PetrifyStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FILL).forGetter(PetrifyStep::fill)
    ).apply(inst, PetrifyStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PetrifyStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PetrifyStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (target instanceof Mob mob) {
            Petrification before = mob.getData(GooAttachments.PETRIFICATION);
            Petrification after = before.fill((float) fill.evaluate(context));
            mob.setData(GooAttachments.PETRIFICATION, after);
            if (after.statue() && !before.statue()) {
                PetrifyEvents.becomeStatue(mob);
            }
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fill);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
