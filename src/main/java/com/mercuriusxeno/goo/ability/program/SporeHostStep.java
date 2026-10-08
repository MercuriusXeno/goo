package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.spray.Spored;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spores the host's target and finishes: until the spores fade, the
 * target's death bursts the named ability's spray from its corpse. Mycosis
 * spores what its cone reaches with
 * {@code spore_host burst=goo:shroom_mycosis radius=3 duration=600}
 * (decision mycosis-spore-stream-buds-and-poisons).
 *
 * @param burst    the ability the corpse bursts
 * @param radius   the burst's reach in blocks, evaluated when the step runs
 * @param duration the ticks the spores last, evaluated when the step runs
 */
public record SporeHostStep(Identifier burst, Expr radius, Expr duration) implements Step {

    private static final String NAME = "spore_host";
    private static final String FIELD_BURST = "burst";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DURATION = "duration";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SporeHostStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_BURST).forGetter(SporeHostStep::burst),
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(SporeHostStep::radius),
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(SporeHostStep::duration)
    ).apply(inst, SporeHostStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SporeHostStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SporeHostStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        long expiresAt = target.level().getGameTime() + duration.evaluateInt(context);
        target.setData(GooAttachments.SPORED, new Spored(burst, radius.evaluateFloat(context), expiresAt));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, duration);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
