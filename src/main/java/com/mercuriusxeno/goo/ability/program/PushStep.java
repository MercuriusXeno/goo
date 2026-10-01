package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets the host's target moving and finishes. Typhoon propulsion is
 * {@code push strength=1.5 direction=thrower_look} on the player host: the
 * player launched along its own look (decision self-delivery-runs-on-player).
 *
 * @param strength  the speed the push sets, in blocks per tick, evaluated when the step runs
 * @param direction which way the push sends the target
 */
public record PushStep(Expr strength, PushDirection direction) implements Step {

    private static final String NAME = "push";
    private static final String FIELD_STRENGTH = "strength";
    private static final String FIELD_DIRECTION = "direction";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PushStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_STRENGTH).forGetter(PushStep::strength),
            PushDirection.CODEC.fieldOf(FIELD_DIRECTION).forGetter(PushStep::direction)
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
