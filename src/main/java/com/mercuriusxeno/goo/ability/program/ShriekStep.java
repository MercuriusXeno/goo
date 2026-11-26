package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The Shriek blob's cry: on its landing tick the blob shrieks like a sculk
 * shrieker and every hostile within reach is called to the landing at once,
 * each walk holding for the ticks the step lasts, and the step ends there,
 * so nothing arriving after the shriek is called. The Shriek blob is
 * {@code shriek reach=16 lasts=600}.
 * decision shriek-blob-cries-once-and-calls-earshot
 * decision shriek-is-a-sculk-call-that-draws-every-mob-it-reaches
 * decision convoke-blob-throbs-until-a-mob-arrives
 *
 * @param reach how far across the ground the shriek is heard, in blocks
 * @param lasts the ticks each called mob's walk holds, counted from the shriek
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
        context.hostAs(ShriekHost.class).shriek(reach.evaluate(context), lasts.evaluateInt(context));
        return true;
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
