package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireGrowth;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spire's step: stone grows into the cells above each planned footprint
 * cell, from one above the ground through the rise, throwing dust off every
 * stone block grown; the ground under it stays as it stood.
 * decision spire-grows-stone-out-of-the-top
 */
public record SpireStep() implements Step {

    private static final String NAME = "spire";
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
     * Grows stone over every planned column, from the cell above the ground up.
     *
     * @param level  the server level
     * @param growth the planned growth
     */
    static void grow(ServerLevel level, SpireGrowth growth) {
        BlockState stone = Blocks.STONE.defaultBlockState();
        int rise = growth.footprint().rise();
        for (BlockPos ground : growth.columns()) {
            for (int height = 1; height <= rise; height++) {
                BlockPos cell = ground.above(height);
                level.setBlockAndUpdate(cell, stone);
                throwDust(level, cell, stone);
            }
        }
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
