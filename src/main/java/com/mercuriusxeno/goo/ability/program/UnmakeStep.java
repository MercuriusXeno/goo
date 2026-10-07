package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Works the held block toward its dissolve: once the work done reaches what
 * the crucible would charge to melt it, times {@code work_per_goo}, the
 * block goes and leaves {@code yield} of its goo; until then the dissolve
 * shows its progress. A block with no goo value stands.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param workPerGoo the work one mB of the block's value costs, evaluated each run
 * @param yield      the share of the block's value left behind, evaluated each run
 */
public record UnmakeStep(Expr workPerGoo, Expr yield) implements Step {

    private static final String NAME = "unmake";
    private static final String FIELD_WORK_PER_GOO = "work_per_goo";
    private static final String FIELD_YIELD = "yield";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<UnmakeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_WORK_PER_GOO).forGetter(UnmakeStep::workPerGoo),
            Expr.CODEC.fieldOf(FIELD_YIELD).forGetter(UnmakeStep::yield)
    ).apply(inst, UnmakeStep::new));

    /**
     * The registered type.
     */
    public static final StepType<UnmakeStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<UnmakeStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        UnmakeHost host = context.hostAs(UnmakeHost.class);
        GooValue value = host.unmadeValue();
        if (value == null || value.isEmpty()) {
            return true;
        }
        int needed = UnmakeRule.workToUnmake(value.totalGoo(), workPerGoo.evaluate(context));
        int done = host.countUnmakeWork();
        if (done >= needed) {
            host.unmake(UnmakeRule.yieldOf(value, yield.evaluate(context)));
        } else {
            host.showUnmaking((float) done / needed);
        }
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(workPerGoo, yield);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.UNMAKE);
    }
}
