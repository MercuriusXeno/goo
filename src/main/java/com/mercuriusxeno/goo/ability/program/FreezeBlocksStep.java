package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Freezes the still water and lava within a sphere around the host's frost
 * center: water becomes magicked ice, which never thaws, and lava becomes
 * obsidian. A frost tap's nova leaves the area it crossed frozen this way
 * (decision nova-drip-pulses-a-short-lasting-freeze).
 *
 * @param radius the sphere's radius in blocks, evaluated on the host
 */
public record FreezeBlocksStep(Expr radius) implements Step {

    private static final String NAME = "freeze_blocks";
    private static final String FIELD_RADIUS = "radius";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<FreezeBlocksStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Expr.CODEC.fieldOf(FIELD_RADIUS).forGetter(FreezeBlocksStep::radius)
    ).apply(inst, FreezeBlocksStep::new));

    /**
     * The registered type.
     */
    public static final StepType<FreezeBlocksStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<FreezeBlocksStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        FrostHost host = context.hostAs(FrostHost.class);
        freezeWithin(host.level(), host.frostCenter(), radius.evaluate(context));
        return true;
    }

    /**
     * Freezes the still water and lava whose block centers stand within a
     * sphere.
     *
     * @param level  the level
     * @param center the sphere's center
     * @param radius the sphere's radius in blocks
     */
    public static void freezeWithin(ServerLevel level, Vec3 center, double radius) {
        BlockPos from = BlockPos.containing(center.subtract(radius, radius, radius));
        BlockPos to = BlockPos.containing(center.add(radius, radius, radius));
        double reachSquared = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(center) <= reachSquared) {
                frozenForm(level.getBlockState(pos)).ifPresent(frozen -> level.setBlock(pos, frozen,
                        Block.UPDATE_ALL));
            }
        }
    }

    /**
     * What a block freezes into: still water to magicked ice, still lava to
     * obsidian, anything else nothing.
     *
     * @param state the block
     * @return its frozen form, or empty where it does not freeze
     */
    static Optional<BlockState> frozenForm(BlockState state) {
        if (!state.getFluidState().isSource()) {
            return Optional.empty();
        }
        if (state.is(Blocks.WATER)) {
            return Optional.of(GooBlocks.MAGICKED_ICE.get().defaultBlockState());
        }
        return state.is(Blocks.LAVA) ? Optional.of(Blocks.OBSIDIAN.defaultBlockState()) : Optional.empty();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(radius);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.FROST);
    }
}
