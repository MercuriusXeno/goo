package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SporesStep;
import com.mercuriusxeno.goo.ability.spray.Spored;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Shroom's Spores cell: the volume cell bound to Spores' goo, a passive
 * Mycosis. On each of its periods it emits its spore particle and places one
 * fungal bud at age 0 on an edge space, a space beside the goo, at its level
 * or just above, that holds nothing and where a fungal bud can stand,
 * spending the fixed layers a bud costs, as SporesGrowth chooses. It ages
 * nothing already standing, so the goo grows to buds and stops and the buds
 * ripen on their own; with no valid edge space it places nothing and keeps
 * its layers. A mob standing on the goo, on its top or in its space, carries
 * spores as the spore stream gives them, refreshed while it stands there,
 * bursting the step's ability when it dies, and no mycosis effect; the
 * spores mark that the goo gave them and credit the cell's placer.
 * spores-is-shrooms-residual-a-passive-mycosis
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
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
     * Spores a mob standing on the goo's top, as every cell's surface then acts on it.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        spore(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Spores a mob in the goo's space, wading in it or standing on a cell short of a whole block.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        spore(level, pos, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Gives a living mob on the goo the spores the step names, lasting its
     * spore ticks from now, marked as the goo's and crediting the cell's
     * placer; a player and a dead mob carry none.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param level  the level the cell stands in
     * @param pos    the cell
     * @param entity the entity on the cell
     */
    private void spore(Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Mob mob) || !mob.isAlive()) {
            return;
        }
        SporesStep.in(layingProgram(server)).ifPresent(spores -> mob.setData(GooAttachments.SPORED,
                new Spored(spores.burst(), spores.sporeRadius(), server.getGameTime() + spores.sporeTicks(), true,
                        Optional.ofNullable(creditedPlayerAt(server, pos)).map(Entity::getUUID))));
    }

    /**
     * The player a death fed by this cell credits: whoever placed it, while that player is on the server.
     * mobs-on-spores-goo-turn-death-into-shrooms-and-mycelium
     *
     * @param level the level
     * @param pos   the cell
     * @return the player, or null where no player placed it or the player is away
     */
    public @Nullable ServerPlayer creditedPlayer(ServerLevel level, BlockPos pos) {
        return creditedPlayerAt(level, pos);
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
