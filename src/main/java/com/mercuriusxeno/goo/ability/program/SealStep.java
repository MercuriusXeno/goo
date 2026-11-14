package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.seal.SealCurse;
import com.mercuriusxeno.goo.ability.seal.SealEvents;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Casts Seal on the host's target and finishes: the first Seal curses it with
 * a warp curse, so it warps away each time it comes within the radius of a
 * player; a Seal on a target already cursed exiles it from existence.
 * Seal is {@code seal radius=6 range=32}.
 * Decision zone-curses-with-ender-shimmer.
 *
 * @param radius how near a player the target may come before it warps, evaluated when the step runs
 * @param range  the full width of a warp's random roll, evaluated when the step runs
 */
public record SealStep(Expr radius, Expr range) implements Step {

    private static final String NAME = "seal";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_RANGE = "range";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SealStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(SealStep::radius),
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(SealStep::range)
    ).apply(inst, SealStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SealStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SealStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        if (SealEvents.curseOf(target) != null && target.level() instanceof ServerLevel level) {
            SealEvents.exile(level, target);
        } else {
            target.setData(GooAttachments.SEAL_CURSE, new SealCurse(radius.evaluateFloat(context),
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
