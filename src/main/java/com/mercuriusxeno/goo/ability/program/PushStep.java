package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets the host's target moving along the thrower's look and finishes. Typhoon
 * propulsion is {@code push strength=1.5} on the player host: the player is its
 * own thrower, so it is launched along its own look
 * (decision self-delivery-runs-on-player).
 *
 * @param strength the speed the push sets, in blocks per tick, evaluated when the step runs
 */
public record PushStep(Expr strength) implements Step {

    private static final String NAME = "push";
    private static final String FIELD_STRENGTH = "strength";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PushStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_STRENGTH).forGetter(PushStep::strength)
    ).apply(inst, PushStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PushStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PushStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        Entity thrower = host.thrower();
        if (thrower != null) {
            host.push(thrower.getLookAngle().scale(strength.evaluate(context)));
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(strength);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
