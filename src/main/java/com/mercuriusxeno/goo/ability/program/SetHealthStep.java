package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets the host's target to a fraction of its current health, bypassing
 * damage, and finishes; nether wither is {@code set_health fraction=0.5}
 * before its wither potion.
 *
 * @param fraction the fraction of current health to keep, evaluated when the step runs
 */
public record SetHealthStep(Expr fraction) implements Step {

    private static final String NAME = "set_health";
    private static final String FIELD_FRACTION = "fraction";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SetHealthStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_FRACTION).forGetter(SetHealthStep::fraction)
    ).apply(inst, SetHealthStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SetHealthStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SetHealthStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.host().target();
        target.setHealth(target.getHealth() * fraction.evaluateFloat(context));
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
