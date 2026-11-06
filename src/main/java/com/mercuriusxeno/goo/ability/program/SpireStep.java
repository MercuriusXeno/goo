package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireGrowth;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spire's step: the stone for the cells above each planned footprint cell,
 * from one above the ground through the rise, rises out of the ground as one
 * hunk of blocks over the rise time, dust thrown off the ground where each
 * column breaks out, and settles into its cells when the rise ends; the
 * ground under it stays as it stood.
 * decision spire-grows-stone-out-of-the-top
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
public record SpireStep() implements Step {

    private static final String NAME = "spire";
    /** The ticks the stone takes to rise from the ground into its cells. */
    public static final int RISE_TICKS = 20;
    private static final int DUST_PER_BLOCK = 6;
    private static final double DUST_SPREAD = 0.4;
    private static final double DUST_SPEED = 0.1;

    /** Codec for the step, which takes no params. */
    public static final MapCodec<SpireStep> CODEC = MapCodec.unit(new SpireStep());

    /** The registered type. */
    public static final StepType<SpireStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SpireStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.host() instanceof RaiseGroundHost ground) {
            grow(ground.level(), ground.growth());
        }
        return true;
    }

    /**
     * Raises stone over every planned column, from the cell above the ground
     * up, as one hunk climbing out of the ground into its cells.
     *
     * @param level  the server level
     * @param growth the planned growth
     */
    static void grow(ServerLevel level, SpireGrowth growth) {
        if (growth.columns().isEmpty()) {
            return;
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        int rise = growth.footprint().rise();
        BlockPos origin = growth.columns().getFirst().above();
        List<BlockHunk.HunkBlock> blocks = new ArrayList<>();
        for (BlockPos ground : growth.columns()) {
            for (int height = 1; height <= rise; height++) {
                blocks.add(new BlockHunk.HunkBlock(ground.above(height).subtract(origin), stone));
            }
            throwDust(level, ground.above(), stone);
        }
        BlockHunk.raise(level, origin, blocks, rise, RISE_TICKS);
    }

    private static void throwDust(ServerLevel level, BlockPos cell, BlockState state) {
        Vec3 center = Vec3.atCenterOf(cell);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), center.x, center.y, center.z,
                DUST_PER_BLOCK, DUST_SPREAD, DUST_SPREAD, DUST_SPREAD, DUST_SPEED);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.RAISE_GROUND);
    }
}
