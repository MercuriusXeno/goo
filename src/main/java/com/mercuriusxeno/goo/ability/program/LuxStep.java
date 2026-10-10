package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.LivingEntity;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Grants the host's target Lux and finishes: night vision without
 * particles, and the glow glisten on the mob it looks at, while Lux stands.
 * Cast from the glove it stands as a held effect, paying its upkeep until
 * ended; drunk as a brew it stands for the brew's hour (decisions
 * lux-night-vision-without-particles, self-effects-trickle-until-ended).
 */
public record LuxStep() implements Step {

    private static final String NAME = "lux";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<LuxStep> CODEC = MapCodec.unit(new LuxStep());

    /**
     * The registered type.
     */
    public static final StepType<LuxStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LuxStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        TargetHost host = context.hostAs(TargetHost.class);
        LivingEntity target = host.target();
        Lux standing = target.getData(GooAttachments.LUX);
        OptionalInt brew = host.brewDuration();
        target.setData(GooAttachments.LUX, brew.isPresent()
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
