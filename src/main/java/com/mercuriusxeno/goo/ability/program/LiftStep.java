package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Lift, typhoon's combo on a prism, as long as the prism stands: each tick
 * every entity standing in the one-block shaft rising from the block above
 * the prism, up to the first block that stops movement, rises at the lift's
 * pace, or sinks gently while sneaking, and stepping sideways out of the
 * shaft steps off it. The client draws frost's wind lines rising up the
 * shaft: {@code lift rise=0.3 sink=0.2 cap=32}.
 * lift-prism-levitates-the-block-above
 *
 * @param rise the rise the shaft carries at, in blocks per tick, evaluated when the step runs
 * @param sink the pace a sneaking rider sinks at, in blocks per tick, evaluated when the step runs
 * @param cap  the tallest the shaft runs, in blocks, evaluated when the step runs
 */
public record LiftStep(Expr rise, Expr sink, Expr cap) implements Step {

    private static final String NAME = "lift";
    private static final String FIELD_RISE = "rise";
    private static final String FIELD_SINK = "sink";
    private static final String FIELD_CAP = "cap";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<LiftStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RISE).forGetter(LiftStep::rise),
            Expr.CODEC.fieldOf(FIELD_SINK).forGetter(LiftStep::sink),
            Expr.CODEC.fieldOf(FIELD_CAP).forGetter(LiftStep::cap)
    ).apply(inst, LiftStep::new));

    /**
     * The registered type.
     */
    public static final StepType<LiftStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<LiftStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(EntityScanHost.class).rideShaftAbove(cap.evaluateInt(context), rise.evaluate(context),
                sink.evaluate(context));
        return false;
    }

    /**
     * The height of the shaft a lift stands above its prism, as the client
     * reads it to draw the rising wind.
     *
     * @param level the world
     * @param prism the lift's prism
     * @param cap   the tallest the shaft runs
     * @return the shaft's height in blocks
     */
    public static int shaftHeightAbove(BlockGetter level, BlockPos prism, int cap) {
        return EntityLift.shaftHeight(level, prism.above(), cap);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(rise, sink, cap);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.TICKING, HostCapability.ENTITY_SCAN);
    }
}
