package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.xeno.Eldritch;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Makes the host's target eldritch and finishes. Cast from the glove the
 * state stands as a held effect until ended; drunk as a brew it stands for
 * the brew's hour.
 * eldritch-sight-reveals-the-out-of-phase
 */
public record EldritchStep() implements Step {

    private static final String NAME = "eldritch";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<EldritchStep> CODEC = MapCodec.unit(new EldritchStep());

    /**
     * The registered type.
     */
    public static final StepType<EldritchStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<EldritchStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        Eldritch standing = target.getData(GooAttachments.ELDRITCH);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.ELDRITCH, brew.isPresent()
                ? standing.brew(brew.getAsInt(), target.level().getGameTime())
                : standing.hold());
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
