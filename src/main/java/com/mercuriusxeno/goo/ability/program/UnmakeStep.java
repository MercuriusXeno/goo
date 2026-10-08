package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.data.GooValue;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Works each block the host holds toward its melt: once the work done on a
 * block reaches the unstable crucible's melt time for it, divided by
 * {@code speed}, the block goes and leaves the full goo the crucible would;
 * until then its melt shows its progress. A block with no goo value stands.
 * A held mob is worked the same way against the loot it would drop, each
 * stack a unit and the slowest deciding, and leaves that goo in place of the
 * items. On a stream it runs in the channel's block pass.
 * decision unmake-waves-dissolve-by-crucible-cost
 *
 * @param speed how much faster than the crucible the unmake works, evaluated each run; 1 at its pace
 */
public record UnmakeStep(Expr speed) implements Step {

    private static final String NAME = "unmake";
    private static final String FIELD_SPEED = "speed";

    /**
     * Codec for the step's params; an unmake naming no speed works at the crucible's pace.
     */
    public static final MapCodec<UnmakeStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.optionalFieldOf(FIELD_SPEED, Expr.literal(1)).forGetter(UnmakeStep::speed)
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
     * Works one held mob as a block is worked, against its loot.
     *
     * @param host    the unmake host
     * @param mob     the held mob
     * @param context the step's context, which the params evaluate against
     */
    private void workMob(UnmakeHost host, LivingEntity mob, StepContext context) {
        UnmakeLoot.Loot loot = host.unmadeLoot(mob);
        if (loot == null) {
            return;
        }
        int needed = UnmakeRule.workToUnmake(loot.slowestUnit(), host.meltExponent(), speed.evaluate(context));
        int done = host.countUnmakeWork(mob);
        if (done >= needed) {
            host.unmake(mob, loot.goo().toGooContents());
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
        int needed = UnmakeRule.workToUnmake(value.totalGoo(), host.meltExponent(), speed.evaluate(context));
        int done = host.countUnmakeWork(pos);
        if (done >= needed) {
            host.unmake(pos, value.toGooContents());
        } else {
            host.showUnmaking(pos, (float) done / needed);
        }
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.UNMAKE);
    }
}
