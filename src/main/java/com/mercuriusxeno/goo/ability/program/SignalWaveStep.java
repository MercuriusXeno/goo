package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Signal's block step, run each held tick of the stream: a wave front runs
 * out from the eye along the aim at the JSON's speed, up to the stream's
 * range, and every lever, button, door, trapdoor or fence gate inside the
 * cone behind the front toggles the first time the front reaches it in the
 * hold, and never again in that hold:
 * {@code signal_wave speed=0.5}.
 * signal-wave-toggles-each-device-once
 *
 * @param speed the blocks the front runs out per tick held, evaluated when the step runs
 */
public record SignalWaveStep(Expr speed) implements Step {

    private static final String NAME = "signal_wave";
    private static final String FIELD_SPEED = "speed";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SignalWaveStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_SPEED).forGetter(SignalWaveStep::speed)
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
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            Vec3 eye = host.eye();
            Vec3 line = aim.aimPoint().subtract(eye);
            double range = line.length();
            double front = frontAt(aim.held(), speed.evaluateFloat(context), range);
            if (front > 0) {
                Vec3 reach = eye.add(line.scale(front / range));
                for (BlockPos pos : CalcifyStep.blocksInCone(eye, reach, aim.coneDegrees())) {
                    host.toggleOnceThisHold(pos);
                }
            }
        });
        return true;
    }

    /**
     * How far the wave front has run out from the eye on a tick of the hold.
     *
     * @param held  the hold's tick count, 1 on its first tick
     * @param speed the blocks the front runs per tick
     * @param range the stream's range in blocks
     * @return the front's distance, from one tick's run on the first tick up to the range
     */
    static double frontAt(int held, double speed, double range) {
        return Math.min(range, Math.max(0, held) * speed);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(speed);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
