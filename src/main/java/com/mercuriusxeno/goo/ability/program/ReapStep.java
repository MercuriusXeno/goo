package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.reap.ReapQueue;
import com.mercuriusxeno.goo.ability.reap.Reaping;
import com.mercuriusxeno.goo.network.BlockVisuals;
import com.mercuriusxeno.goo.network.ReapSwellPayload;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Reap's step, run where the blob strikes: a whole sphere of Growth's breeze
 * swells from the struck point out to the radius over the swell's ticks, and
 * each ripe plant within it is reaped as the swell reaches it, nearest first,
 * its afterimage lifting off; then the step finishes. Leaf Reap is
 * {@code reap radius=4 swell_ticks=10}.
 * reap-breeze-harvests-and-replants
 *
 * @param radius     how far the swell reaches, in blocks
 * @param swellTicks the ticks the swell takes to reach it
 */
public record ReapStep(Expr radius, Expr swellTicks) implements Step {

    private static final String NAME = "reap";
    private static final String FIELD_RADIUS = "radius";
    private static final String FIELD_SWELL_TICKS = "swell_ticks";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ReapStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(ReapStep::radius),
            Expr.CODEC.fieldOf(FIELD_SWELL_TICKS).forGetter(ReapStep::swellTicks)
    ).apply(inst, ReapStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ReapStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ReapStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        AnchoredWorldHost host = context.hostAs(AnchoredWorldHost.class);
        ServerLevel level = host.level();
        Vec3 center = host.anchor();
        double reach = radius.evaluate(context);
        int swell = swellTicks.evaluateInt(context);
        BlockVisuals.sendToWatchers(level, BlockPos.containing(center), new ReapSwellPayload(center, (float) reach,
                swell));
        long now = level.getGameTime();
        int span = (int) Math.ceil(reach);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(center).offset(-span, -span, -span),
                BlockPos.containing(center).offset(span, span, span))) {
            double distance = Vec3.atCenterOf(pos).distanceTo(center);
            if (distance <= reach && Reaping.ripe(level.getBlockState(pos))) {
                ReapQueue.schedule(level, pos, now + reachedAfter(distance, reach, swell));
            }
        }
        return true;
    }

    /**
     * The ticks the swell takes to reach a point, on the ease-out it swells on.
     *
     * @param distance how far the point stands from where the swell starts
     * @param reach    the radius the swell reaches
     * @param swell    the ticks the swell takes to reach its radius
     * @return the ticks until the swell reaches the point
     */
    static long reachedAfter(double distance, double reach, int swell) {
        double share = Math.clamp(distance / reach, 0, 1);
        // The swell eases out on a cubic: it reaches a share of its radius when 1 - (1 - t)^3 equals it.
        double time = 1 - Math.cbrt(1 - share);
        return Math.round(time * swell);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius, swellTicks);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.BREAK_BLOCKS);
    }
}
