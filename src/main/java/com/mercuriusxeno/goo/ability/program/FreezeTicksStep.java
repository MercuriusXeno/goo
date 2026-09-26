package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Adds to the host's target's frozen ticks and finishes; a full freeze
 * stands at 140, so frost snap is {@code freeze_ticks add=140}.
 *
 * @param add the ticks to add, evaluated when the step runs
 */
public record FreezeTicksStep(Expr add) implements Step {

    private static final String NAME = "freeze_ticks";
    private static final String FIELD_ADD = "add";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FreezeTicksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_ADD).forGetter(FreezeTicksStep::add)
    ).apply(inst, FreezeTicksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FreezeTicksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FreezeTicksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        target.setTicksFrozen(target.getTicksFrozen() + add.evaluateInt(context));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(add);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
