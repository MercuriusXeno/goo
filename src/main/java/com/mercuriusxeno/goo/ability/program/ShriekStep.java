package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The Shriek blob's cry: on its first tick the blob shrieks like a sculk
 * shrieker and every hostile within reach is called to it at once, the
 * walk holding while the blob lasts; every later tick only counts toward
 * its lifetime, so nothing arriving after the shriek is called, and the
 * blob goes once it has lasted. The Shriek blob is
 * {@code shriek reach=16 lasts=600}.
 * decision shriek-blob-cries-once-and-calls-earshot
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 *
 * @param reach how far across the ground the shriek is heard, in blocks
 * @param lasts the ticks the blob lasts
 */
public record ShriekStep(Expr reach, Expr lasts) implements Step {

    private static final String NAME = "shriek";
    private static final String FIELD_REACH = "reach";
    private static final String FIELD_LASTS = "lasts";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ShriekStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_REACH).forGetter(ShriekStep::reach),
            Expr.CODEC.fieldOf(FIELD_LASTS).forGetter(ShriekStep::lasts)
    ).apply(inst, ShriekStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ShriekStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ShriekStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.stepTicks() == 0) {
            context.hostAs(ShriekHost.class).shriek(reach.evaluate(context));
        }
        return context.stepTicks() + 1 >= lasts.evaluateInt(context);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(reach, lasts);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.SHRIEK);
    }
}
