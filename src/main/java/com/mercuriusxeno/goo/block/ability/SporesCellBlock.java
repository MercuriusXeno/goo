package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SporesStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.NonNull;
import java.util.List;
import java.util.stream.Stream;

/**
 * Shroom's Spores cell: the volume cell bound to Spores' goo, a passive
 * Mycosis. On each of its periods it emits its spore particle and places one
 * fungal bud at age 0 on an edge space, a space beside the goo, at its level
 * or just above, that holds nothing and where a fungal bud can stand,
 * spending the fixed layers a bud costs, as SporesGrowth chooses. It ages
 * nothing already standing, so the goo grows to buds and stops and the buds
 * ripen on their own; with no valid edge space it places nothing and keeps
 * its layers.
 * spores-is-shrooms-residual-a-passive-mycosis
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 */
public class SporesCellBlock extends VolumeCellBlock {

    /** How far over the cell's top its spores rise from. */
    private static final double SPORE_LIFT = 0.2;
    /** The spores emitted a period. */
    private static final int SPORE_COUNT = 6;
    /** How far across the cell the spores spread. */
    private static final double SPORE_SPREAD = 0.3;
    /** How fast the spores drift. */
    private static final double SPORE_SPEED = 0.01;
    private static final double HALF = 0.5;

    /**
     * Creates a Spores cell.
     *
     * @param properties the block properties
     * @param binding    the Spores binding
     */
    public SporesCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
        registerDefaultState(defaultBlockState().setValue(GoologgedFungus.FUNGUS, GoologgedFungus.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GoologgedFungus.FUNGUS);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, emits its
     * spores and buds an edge on a period's tick, and keeps a tick coming, a
     * pending pour's or the next period's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        SporesStep.in(layingProgram(level)).ifPresent(spores -> {
            long now = level.getGameTime();
            spores.emissionAt(now, pos).ifPresent(emission -> emit(level, emission));
            if (spores.onPeriod(now)) {
                budAnEdge(level, pos, spores);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, spores.ticksToNextPeriod(now));
            }
        });
    }

    /**
     * Sends the cell's spore particle rising off its top.
     *
     * @param level    the server level
     * @param emission what the cell emits and where
     */
    private static void emit(ServerLevel level, SporesStep.SporeEmission emission) {
        BlockPos cell = emission.cell();
        SimpleParticles.resolve(emission.particle()).ifPresent(particle -> level.sendParticles(particle,
                cell.getX() + HALF, cell.getY() + layersOf(level.getBlockState(cell)) / (double) MAX_LAYERS
                        + SPORE_LIFT, cell.getZ() + HALF, SPORE_COUNT, SPORE_SPREAD, SPORE_LIFT, SPORE_SPREAD,
                SPORE_SPEED));
    }

    /**
     * Places the next bud SporesGrowth answers on an edge space and stands
     * the cell lower by its layers, gone where they were its last.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @param level  the server level
     * @param pos    the cell
     * @param spores Spores' step
     */
    private void budAnEdge(ServerLevel level, BlockPos pos, SporesStep spores) {
        BlockState standing = level.getBlockState(pos);
        BlockState bud = spores.budState();
        SporesGrowth.Budding budding = SporesGrowth.nextBud(edges(level, pos, bud), level.getRandom().nextInt(),
                standing.getValue(LAYERS), spores.layersPerBud());
        budding.spot().ifPresent(spot -> {
            level.setBlock(spot, bud, UPDATE_ALL);
            level.setBlock(pos, budding.layersLeft() > 0 ? standing.setValue(LAYERS, budding.layersLeft())
                    : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        });
    }

    /**
     * The spaces beside a cell, its four sides at its level and the four just
     * above them, each read for whether it holds nothing and whether a fungal
     * bud can stand there.
     *
     * @param level the server level
     * @param pos   the cell
     * @param bud   the fungal bud's state, whose rule says where it can stand
     * @return the edge spaces
     */
    private static List<SporesGrowth.Edge> edges(ServerLevel level, BlockPos pos, BlockState bud) {
        return Direction.Plane.HORIZONTAL.stream()
                .flatMap(side -> Stream.of(pos.relative(side), pos.relative(side).above()))
                .map(space -> new SporesGrowth.Edge(space, level.getBlockState(space).isAir(),
                        bud.canSurvive(level, space)))
                .toList();
    }

    /**
     * A Spores cell remembers who placed it, so what its goo bursts credits that player.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }
}
