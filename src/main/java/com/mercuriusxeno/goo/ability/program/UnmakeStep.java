package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Works each block the host holds toward its dissolve: once the work done on
 * a block reaches what the crucible would charge to melt it, times
 * {@code work_per_goo}, the block goes and leaves {@code yield} of its goo;
 * until then its dissolve shows its progress. A block with no goo value
 * stands. A held mob is worked the same way against the goo value of the
 * loot it would drop, and leaves that goo in place of the items. On a stream
 * it runs in the channel's block pass.
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
        for (BlockPos pos : host.unmadeBlocks()) {
            workBlock(host, pos, context);
        }
        for (LivingEntity mob : host.unmadeMobs()) {
            workMob(host, mob, context);
        }
        return true;
    }

    /**
     * Works one held mob as a block is worked, against its loot's value.
     *
     * @param host    the unmake host
     * @param mob     the held mob
     * @param context the step's context, which the params evaluate against
     */
    private void workMob(UnmakeHost host, LivingEntity mob, StepContext context) {
        GooValue value = host.unmadeValue(mob);
        if (value == null || value.isEmpty()) {
            return;
        }
        int needed = UnmakeRule.workToUnmake(value.totalGoo(), workPerGoo.evaluate(context));
        int done = host.countUnmakeWork(mob);
        if (done >= needed) {
            host.unmake(mob, UnmakeRule.yieldOf(value, yield.evaluate(context)));
        } else {
            host.showUnmaking(mob, (float) done / needed);
        }
    }

    /**
     * Works one held block: dissolves it once its work is done, else shows its share.
     *
     * @param host    the unmake host
     * @param pos     the held block
     * @param context the step's context, which the params evaluate against
     */
    private void workBlock(UnmakeHost host, BlockPos pos, StepContext context) {
        GooValue value = host.unmadeValue(pos);
        if (value == null || value.isEmpty()) {
            return;
        }
        int needed = UnmakeRule.workToUnmake(value.totalGoo(), workPerGoo.evaluate(context));
        int done = host.countUnmakeWork(pos);
        if (done >= needed) {
            host.unmake(pos, UnmakeRule.yieldOf(value, yield.evaluate(context)));
        } else {
            host.showUnmaking(pos, (float) done / needed);
        }
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
