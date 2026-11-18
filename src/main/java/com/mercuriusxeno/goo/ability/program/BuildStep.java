package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BuildBounds;
import com.mercuriusxeno.goo.ability.BuildColumn;
import com.mercuriusxeno.goo.ability.BuildGrowth;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Build's step: each planned column's reached blocks and the stone grown
 * under them rise together as one hunk of blocks over the rise time, the
 * reached cells emptied as it starts, dust thrown off the ground where each
 * column breaks out, and settle into their cells when the rise ends, the
 * lifted blocks standing the rise higher on the stone; a footprint on a
 * wall or a ceiling pushes them out along the face's normal instead.
 * decision build-goes-sideways
 * decision spire-grows-stone-out-of-the-top
 * decision build-reaches-down-as-far-as-it-rises-and-lifts-the-ground-on-stone
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 * decision build-dimensions-reach-sixteen-by-sixteen-by-sixteen
 *
 * @param maxSpan the widest the footprint runs on either axis, in blocks
 * @param maxRise the highest the Build rises, in blocks
 */
public record BuildStep(int maxSpan, int maxRise) implements Step {

    private static final String NAME = "build";
    /** The ticks the stone takes to rise from the ground into its cells. */
    public static final int RISE_TICKS = 20;
    private static final int DUST_PER_BLOCK = 6;
    private static final double DUST_SPREAD = 0.4;
    private static final double DUST_SPEED = 0.1;

    private static final String FIELD_MAX_SPAN = "max_span";
    private static final String FIELD_MAX_RISE = "max_rise";

    /** Codec for the step: its bounds, each sixteen where the file names none. */
    public static final MapCodec<BuildStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf(FIELD_MAX_SPAN, BuildBounds.SIXTEEN_CUBED.maxSpan())
                    .forGetter(BuildStep::maxSpan),
            Codec.intRange(BuildBounds.MIN_RISE, Integer.MAX_VALUE)
                    .optionalFieldOf(FIELD_MAX_RISE, BuildBounds.SIXTEEN_CUBED.maxRise()).forGetter(BuildStep::maxRise)
    ).apply(inst, BuildStep::new));

    /** The registered type. */
    public static final StepType<BuildStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The bounds the step caps a cast at.
     *
     * @return the span and the rise
     */
    public BuildBounds bounds() {
        return new BuildBounds(maxSpan, maxRise);
    }

    /**
     * The bounds an ability's program caps its cast at: its build step's, or
     * sixteen by sixteen by sixteen where it runs none.
     *
     * @param behaviors the ability's step program
     * @return the bounds
     */
    public static BuildBounds boundsOf(List<Step> behaviors) {
        return behaviors.stream().filter(BuildStep.class::isInstance).map(BuildStep.class::cast).findFirst()
                .map(BuildStep::bounds).orElse(BuildBounds.SIXTEEN_CUBED);
    }

    @Override
    public StepType<BuildStep> type() {
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
     * Lifts every planned column's reached blocks on the stone grown under
     * them, as one hunk climbing the rise into its cells.
     *
     * @param level  the server level
     * @param growth the planned growth
     */
    static void grow(ServerLevel level, BuildGrowth growth) {
        if (growth.columns().isEmpty()) {
            return;
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        Direction face = growth.footprint().face();
        BlockPos origin = growth.columns().getFirst().ground().relative(face);
        List<BlockHunk.HunkBlock> blocks = new ArrayList<>();
        for (BuildColumn column : growth.columns()) {
            column.stoneCells().forEach(cell -> blocks.add(new BlockHunk.HunkBlock(cell.subtract(origin), stone)));
            for (BlockPos cell : column.liftedCells()) {
                BlockState lifted = level.getBlockState(cell);
                if (!lifted.isAir()) {
                    blocks.add(new BlockHunk.HunkBlock(column.liftedTo(cell).subtract(origin), lifted));
                }
            }
            throwDust(level, column.ground().relative(face), stone);
        }
        emptyTheReach(level, growth.columns());
        BlockHunk.raise(level, origin, blocks, growth.footprint().rise(), face, RISE_TICKS);
    }

    /**
     * Empties the cells the columns reached, which the hunk now carries,
     * telling the clients alone, so no neighbor falls or flows into them
     * while the hunk fills them.
     *
     * @param level   the server level
     * @param columns the planned columns
     */
    private static void emptyTheReach(ServerLevel level, List<BuildColumn> columns) {
        BlockState air = Blocks.AIR.defaultBlockState();
        columns.forEach(column -> column.liftedCells().forEach(cell -> level.setBlock(cell, air, Block.UPDATE_CLIENTS)));
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
