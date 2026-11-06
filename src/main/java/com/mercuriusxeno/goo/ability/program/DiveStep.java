package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.deep.Dive;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target the dive and finishes: it swims through solid
 * blocks and sees the way the deep sees. Cast from the glove it stands as a
 * held effect, paying its upkeep until ended; drunk as a brew it stands for
 * the brew's hour. Dive is {@code dive}.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 */
public record DiveStep() implements Step {

    private static final String NAME = "dive";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<DiveStep> CODEC = MapCodec.unit(DiveStep::new);

    /**
     * The registered type.
     */
    public static final StepType<DiveStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<DiveStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        Dive standing = target.getData(GooAttachments.DIVE);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.DIVE, brew.isPresent()
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
