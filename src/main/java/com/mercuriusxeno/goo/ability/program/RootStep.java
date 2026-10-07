package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.root.RootEvents;
import com.mercuriusxeno.goo.ability.root.Rooted;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Roots the host's target in vines where it stands, and finishes: the vines
 * hold it for the duration or the hits, thorn it for straining past the
 * leash and multiply fire damage, and a throw landing on vines still
 * holding stacks its hits and thorns onto them. Leaf Vines is
 * {@code root duration=60 hits=4 thorns=1 fire_factor=1.5}.
 * vines-unpack-root-and-thorn
 *
 * @param duration   the ticks the vines hold the target
 * @param hits       the hits the vines take before they break
 * @param thorns     the thorn damage the throw adds
 * @param fireFactor what fire damage is multiplied by while the vines hold
 */
public record RootStep(Expr duration, Expr hits, Expr thorns, Expr fireFactor) implements Step {

    private static final String NAME = "root";
    private static final String FIELD_DURATION = "duration";
    private static final String FIELD_HITS = "hits";
    private static final String FIELD_THORNS = "thorns";
    private static final String FIELD_FIRE_FACTOR = "fire_factor";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<RootStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_DURATION).forGetter(RootStep::duration),
            Expr.CODEC.fieldOf(FIELD_HITS).forGetter(RootStep::hits),
            Expr.CODEC.fieldOf(FIELD_THORNS).forGetter(RootStep::thorns),
            Expr.CODEC.fieldOf(FIELD_FIRE_FACTOR).forGetter(RootStep::fireFactor)
    ).apply(inst, RootStep::new));

    /**
     * The registered type.
     */
    public static final StepType<RootStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<RootStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Rooted.Throw thrown = new Rooted.Throw(hits.evaluateInt(context), (float) thorns.evaluate(context),
                (float) fireFactor.evaluate(context), duration.evaluateInt(context));
        RootEvents.latch(context.hostAs(TargetHost.class).target(), thrown);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(duration, hits, thorns, fireFactor);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TARGET);
    }
}
