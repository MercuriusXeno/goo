package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.deep.Sculker;
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
 * the brew's hour. Sculker is {@code sculker}.
 * decision dive-is-a-brew-of-warden-senses-and-free-swimming-through-solid-blocks
 * decision dive-is-renamed-sculker
 */
public record SculkerStep() implements Step {

    private static final String NAME = "sculker";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<SculkerStep> CODEC = MapCodec.unit(SculkerStep::new);

    /**
     * The registered type.
     */
    public static final StepType<SculkerStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SculkerStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        Sculker standing = target.getData(GooAttachments.SCULKER);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.SCULKER, brew.isPresent()
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
