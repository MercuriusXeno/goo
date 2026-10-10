package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.network.EntityVisuals;
import com.mercuriusxeno.goo.network.TomePayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Plays a floating tome in front of the host's target and finishes: every
 * client tracking the target, the target among them, draws the book or
 * books and plays their choreography with its sounds. Enchant is
 * {@code tome kind=enchant}; Fuse is {@code tome kind=fuse}. The client also
 * reads this step off a held self ability to fade the tome in while the
 * press is held.
 * enchant-book-with-a-purple-afterimage
 * fuse-two-books-for-hex-goo
 *
 * @param kind the choreography played
 */
public record TomeStep(TomeKind kind) implements Step {

    private static final String NAME = "tome";
    private static final String FIELD_KIND = "kind";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<TomeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TomeKind.CODEC.fieldOf(FIELD_KIND).forGetter(TomeStep::kind)
    ).apply(inst, TomeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<TomeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<TomeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LivingEntity target = context.hostAs(TargetHost.class).target();
        EntityVisuals.sendToWatchers(target, new TomePayload(target.getId(), kind));
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
