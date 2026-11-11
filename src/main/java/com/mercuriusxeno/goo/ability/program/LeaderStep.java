package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zoo.Leader;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Zoo's Leader, laid on the player holding its held effect on the glove:
 * the Leader stands with its radius and short distance until the held effect
 * ends, and the tick subscribers reading it sense and lead the mobs around.
 * leader-is-zoos-brew-ability
 *
 * @param radius   the blocks around the player Leader reaches
 * @param distance the blocks short of the player a led mob holds at
 */
public record LeaderStep(double radius, double distance) implements Step {

    private static final String NAME = "leader";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DISTANCE = "distance";

    /** Codec for the step's params. */
    public static final MapCodec<LeaderStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(LeaderStep::radius),
            Codec.DOUBLE.fieldOf(FIELD_DISTANCE).forGetter(LeaderStep::distance)
    ).apply(inst, LeaderStep::new));

    /** The registered type. */
    public static final StepType<LeaderStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LeaderStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(TargetHost.class).target().setData(GooAttachments.LEADER, new Leader(radius, distance));
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
