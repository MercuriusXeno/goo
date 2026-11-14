package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.zoo.Kin;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Zoo's Kin, laid on the player holding its held effect on the glove:
 * the Kin stands with its radius and short distance until the held effect
 * ends, and the tick subscribers reading it sense and lead the mobs around.
 * leader-is-zoos-brew-ability
 *
 * @param radius   the blocks around the player Kin reaches
 * @param distance the blocks short of the player a led mob holds at
 */
public record KinStep(double radius, double distance) implements Step {

    private static final String NAME = "kin";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_DISTANCE = "distance";

    /** Codec for the step's params. */
    public static final MapCodec<KinStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.DOUBLE.fieldOf(FIELD_RADIUS).forGetter(KinStep::radius),
            Codec.DOUBLE.fieldOf(FIELD_DISTANCE).forGetter(KinStep::distance)
    ).apply(inst, KinStep::new));

    /** The registered type. */
    public static final StepType<KinStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<KinStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(TargetHost.class).target().setData(GooAttachments.KIN, new Kin(radius, distance));
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
