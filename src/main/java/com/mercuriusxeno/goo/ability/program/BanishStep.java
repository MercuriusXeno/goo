package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.banish.BanishEvents;
import com.mercuriusxeno.goo.ability.banish.Banished;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Banishes the host's target and finishes: the first Banish curses it with
 * teleportitis, so it warps away each time it comes within the radius of a
 * player; a Banish on a target already cursed exiles it from existence.
 * Banish is {@code banish radius=6 range=32}.
 * Decision banish-curses-with-ender-shimmer.
 *
 * @param radius how near a player the target may come before it warps, evaluated when the step runs
 * @param range  the full width of a warp's random roll, evaluated when the step runs
 */
public record BanishStep(Expr radius, Expr range) implements Step {

    private static final String NAME = "banish";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_RANGE = "range";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<BanishStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(BanishStep::radius),
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(BanishStep::range)
    ).apply(inst, BanishStep::new));

    /**
     * The registered type.
     */
    public static final StepType<BanishStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<BanishStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (BanishEvents.curseOf(target) != null && target.level() instanceof ServerLevel level) {
            BanishEvents.exile(level, target);
        } else {
            target.setData(GooAttachments.BANISHED, new Banished(radius.evaluateFloat(context),
                    range.evaluateFloat(context)));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, range);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
