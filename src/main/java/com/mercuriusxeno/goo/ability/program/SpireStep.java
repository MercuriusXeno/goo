package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.SpireLift;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Spire's step: each planned column of ground rises out of the footprint by
 * the rise, the ground's own blocks standing as the wall or platform, and
 * the gap under them refills with the mundane block of its depth, throwing
 * dust off every risen block.
 * decision spire-rips-walls-and-platforms
 *
 * @param lifts the block tag naming the blocks Spire may lift
 */
public record SpireStep(TagKey<Block> lifts) implements Step {

    private static final String NAME = "spire";
    private static final String FIELD_LIFTS = "lifts";
    private static final int DUST_PER_BLOCK = 6;
    private static final double DUST_SPREAD = 0.4;
    private static final double DUST_SPEED = 0.1;

    /** Codec for the step's params. */
    public static final MapCodec<SpireStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            TagKey.codec(Registries.BLOCK).fieldOf(FIELD_LIFTS).forGetter(SpireStep::lifts)
    ).apply(inst, SpireStep::new));

    /** The registered type. */
    public static final StepType<SpireStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The block tag a Spire program lifts, which the server plans the lift
     * and its price by before the program runs.
     *
     * @param steps the ability's program
     * @return the tag, or empty where the program holds no Spire step
     */
    public static Optional<TagKey<Block>> liftsOf(List<Step> steps) {
        return steps.stream().filter(SpireStep.class::isInstance).map(step -> ((SpireStep) step).lifts()).findFirst();
    }

    @Override
    public StepType<SpireStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        if (context.host() instanceof RaiseGroundHost ground) {
            raise(ground.level(), ground.lift());
        }
        return true;
    }

    /**
     * Lifts every planned column: the ground's blocks move up by the rise and
     * the cells they left take the refill of their depth.
     *
     * @param level the server level
     * @param lift  the planned lift
     */
    static void raise(ServerLevel level, SpireLift lift) {
        int rise = lift.footprint().rise();
        for (BlockPos ground : lift.columns()) {
            List<BlockPos> column = SpireLift.columnCells(ground, rise);
            List<BlockState> lifted = new ArrayList<>(column.size());
            for (BlockPos cell : column) {
                lifted.add(level.getBlockState(cell));
            }
            // the wall stands from its foot up, so nothing it carries stands over air
            for (int index = column.size() - 1; index >= 0; index--) {
                BlockPos cell = column.get(index);
                BlockPos risen = cell.above(rise);
                level.setBlockAndUpdate(risen, lifted.get(index));
                level.setBlockAndUpdate(cell, SpireLift.refillAt(cell.getY()));
                throwDust(level, risen, lifted.get(index));
            }
        }
    }

    private static void throwDust(ServerLevel level, BlockPos risen, BlockState state) {
        Vec3 center = Vec3.atCenterOf(risen);
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
