package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.hex.Lifetap;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target a lifetap and finishes: food no longer
 * regenerates their health, and damage they deal heals them by the
 * fraction. Cast from the glove it stands as a held effect, paying its
 * upkeep until ended; drunk as a brew it stands for the brew's hour.
 * Lifetap is {@code lifetap fraction=0.3}
 * (decisions lifetap-trades-regen-for-leech, self-effects-trickle-until-ended).
 *
 * @param fraction the share of the damage dealt that heals, evaluated when the step runs
 */
public record LifetapStep(Expr fraction) implements Step {

    private static final String NAME = "lifetap";
    private static final String FIELD_FRACTION = "fraction";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LifetapStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FRACTION).forGetter(LifetapStep::fraction)
    ).apply(inst, LifetapStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LifetapStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LifetapStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        float leech = fraction.evaluateFloat(context);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.LIFETAP, brew.isPresent()
                ? target.getData(GooAttachments.LIFETAP).brew(leech, brew.getAsInt(), target.level().getGameTime())
                : Lifetap.hold(leech));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(fraction);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
