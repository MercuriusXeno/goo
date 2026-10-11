package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Zap's dispersal: a Zap landing on anything but a redstone device disperses
 * into the Signal wave at the landing, which passes through the struck block
 * and toggles each lever, button, door, trapdoor or fence gate inside the
 * cone behind it, out to the range, once:
 * {@code signal_wave range=8 cone=40}.
 * zap-disperses-into-signal
 *
 * @param range the blocks the wave reaches past the landing, evaluated when the step runs
 * @param cone  the wave's cone, apex to rim, in degrees, evaluated when the step runs
 */
public record SignalWaveStep(Expr range, Expr cone) implements Step {

    private static final String NAME = "signal_wave";
    private static final String FIELD_RANGE = "range";
    private static final String FIELD_CONE = "cone";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SignalWaveStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RANGE).forGetter(SignalWaveStep::range),
            Expr.CODEC.fieldOf(FIELD_CONE).forGetter(SignalWaveStep::cone)
    ).apply(inst, SignalWaveStep::new));

    /**
     * The registered type.
     */
    public static final StepType<SignalWaveStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SignalWaveStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(PowerPulseHost.class).signalWave(range.evaluateFloat(context), cone.evaluateFloat(context));
        return true;
    }

    /**
     * The cells the wave reaches: from the landing cell's center into the
     * struck face and on through it, the cells along that line and inside
     * the cone around it, nearest first.
     *
     * @param cell        the cell the blob landed in
     * @param face        the struck block's face the blob landed on
     * @param range       the blocks the wave reaches
     * @param coneDegrees the cone, apex to rim, in degrees
     * @return the cells the wave crosses
     */
    public static List<BlockPos> cellsBehind(BlockPos cell, Direction face, double range, double coneDegrees) {
        Vec3 apex = Vec3.atCenterOf(cell);
        return AimedCells.along(apex, apex.add(face.getOpposite().getUnitVec3().scale(range)), coneDegrees);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(range, cone);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.POWER_PULSE);
    }
}
