package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A stream's block pass that cools every lava block, source or flowing,
 * inside its cone into a magma block each held tick, still harmful
 * underfoot but far less than the lava it replaced; Coat's slime cools the
 * lava it reaches: {@code cool_lava}.
 * weird-bounces-and-softens-harm
 */
public record CoolLavaStep() implements Step {

    private static final String NAME = "cool_lava";

    /**
     * Codec for the step, which takes no params.
     */
    public static final MapCodec<CoolLavaStep> CODEC = MapCodec.unit(CoolLavaStep::new);

    /**
     * The registered type.
     */
    public static final StepType<CoolLavaStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<CoolLavaStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        ChannelHost host = context.hostAs(ChannelHost.class);
        ServerLevel level = context.hostAs(FrostHost.class).level();
        host.channelAim().ifPresent(aim -> {
            for (BlockPos pos : CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees())) {
                if (level.getFluidState(pos).is(FluidTags.LAVA)) {
                    level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
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
        return Set.of(HostCapability.CHANNEL, HostCapability.FROST);
    }
}
