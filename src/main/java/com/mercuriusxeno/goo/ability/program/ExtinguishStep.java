package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.frost.FrostFire;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A stream's block pass that puts out every fire block inside its cone each
 * held tick, with the extinguish hiss and smoke, and clears the fire on every
 * burning living thing the cone reaches; Cold puts out the fire its wind
 * reaches: {@code extinguish}.
 * frost-reach-extinguishes-fire-and-burning-mobs
 */
public record ExtinguishStep() implements Step {

    private static final String NAME = "extinguish";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<ExtinguishStep> CODEC = MapCodec.unit(ExtinguishStep::new);

    /**
     * The registered type.
     */
    public static final StepType<ExtinguishStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<ExtinguishStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        ServerLevel level = context.hostAs(FrostHost.class).level();
        host.channelAim().ifPresent(aim -> {
            List<BlockPos> cone = CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees());
            for (BlockPos pos : cone) {
                if (FrostFire.burns(level.getBlockState(pos))) {
                    FrostFire.putOut(level, pos);
                }
            }
            host.forEachLivingIn(cone, Set.of(), reached -> reached.target().clearFire());
        });
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL, HostCapability.FROST);
    }
}
