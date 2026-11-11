package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A stream's block pass that cools every source lava block inside its cone
 * into a magma block each held tick, still harmful underfoot but far less
 * than the lava it replaced, and leaves flowing lava as it is; Goop's slime
 * cools the lava it reaches: {@code cool_lava}.
 * weird-bounces-and-softens-harm
 * weird-cools-only-source-lava
 */
public record CoolLavaStep() implements TakesNoEntity {

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
        host.channelAim().ifPresent(aim -> coolSourceLava(level,
                CalcifyStep.blocksInCone(host.eye(), aim.aimPoint(), aim.coneDegrees()),
                Blocks.MAGMA_BLOCK.defaultBlockState()));
        return true;
    }

    /**
     * Turns each source lava cell among the given cells into magma.
     *
     * @param level the level
     * @param cells the cells the stream's cone reaches
     * @param magma the state a cooled cell takes
     */
    static void coolSourceLava(Level level, Iterable<BlockPos> cells, BlockState magma) {
        for (BlockPos pos : cells) {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.is(FluidTags.LAVA) && fluid.isSource()) {
                level.setBlock(pos, magma, Block.UPDATE_ALL);
            }
        }
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
