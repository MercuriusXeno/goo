package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Jelly Heal's block step, run each held tick of the stream: every statue
 * standing in the cone wakes back into its mob, still nearly stone, for the
 * stream's mob step to drain the rest of the stone off.
 * decision heal-wakes-a-statue-at-a-nearly-full-gauge
 */
public record WakeStatueStep() implements TakesNoEntity {

    private static final String NAME = "wake_statue";

    /**
     * Codec for the step, which holds no params.
     */
    public static final MapCodec<WakeStatueStep> CODEC = MapCodec.unit(WakeStatueStep::new);

    /**
     * The registered type.
     */
    public static final StepType<WakeStatueStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<WakeStatueStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        host.channelAim().ifPresent(aim -> {
            for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
                if (host.statueAt(pos)) {
                    host.wakeStatue(pos);
                }
            }
        });
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
