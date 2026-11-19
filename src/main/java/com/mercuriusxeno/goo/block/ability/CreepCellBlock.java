package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.CreepStep;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.registry.GooServerState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SculkBehaviour;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sculk's Creep cell: the volume cell bound to Creep's sculky slime.
 *
 * A sculk charge treats it as ground it runs through. Vanilla's charge cursor moves only onto
 * blocks answering SculkBehaviour, so a charge reaching a Creep cell enters it and travels from Creep cell to Creep
 * cell keeping its whole charge, where on ordinary sculk it would decay; at each cell it crosses, the block under the
 * cell turns to sculk where the spreader's replaceable tag allows, and the cell stands lower by the layers the step
 * names a block of sculk spends, gone where that empties it. A charge leaving the goo onto sculk or veins behaves as
 * vanilla's again.
 *
 * Now and then it puffs its spore particle, as mycelium does, and on each of its periods it places
 * one fungal bud at age 0 on an edge space, a space beside the goo, at its level or just above, that holds nothing
 * and where a fungal bud can stand, spending the fixed layers a bud costs, as CreepGrowth chooses. It ages nothing
 * already standing, and with no valid edge space it places nothing and keeps its layers. A death near it
 * feeds the goo's sculk charge.
 * host-and-spores-combine-into-one-residual
 * sculk-charge-runs-along-host-goo-without-weakening
 * host-is-a-sculky-slime-that-sculk-spreads-eagerly-through
 * spores-goo-spends-on-edge-growth-and-grows-to-buds
 */
public class CreepCellBlock extends VolumeCellBlock implements SculkBehaviour {

    /** How far over the cell's top its spores rise from. */
    private static final double SPORE_LIFT = 0.2;
    /** How fast a puffed spore drifts up. */
    private static final double SPORE_SPEED = 0.01;

    /**
     * Creates a Creep cell.
     *
     * @param properties the block properties
     * @param binding    the Creep binding
     */
    public CreepCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * Turns the block under the cell to sculk, spending the cell's layers,
     * and passes the charge on whole, ending it only where it is stranded:
     * no Creep cell it can reach turns its block and no step leads off the goo.
     */
    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, LevelAccessor level, BlockPos originPos,
                                RandomSource random, SculkSpreader spreader, boolean spreadVeins) {
        BlockPos pos = cursor.getPos();
        if (!(level instanceof Level world) || !level.getBlockState(pos).is(this)) {
            return cursor.getCharge();
        }
        int sculkLayers = CreepStep.in(layingProgram(world)).map(CreepStep::sculkLayers).orElse(0);
        if (sculkLayers == 0) {
            return cursor.getCharge();
        }
        turnTheBlockUnder(world, pos, spreader, sculkLayers);
        return CreepSpread.chargeOut(cursor.getCharge(), stranded(world, pos, spreader, sculkLayers));
    }

    /**
     * Whether a charge standing in a cell is stranded, walking the goo only
     * where no recent walk found the cell free, and recording the cells a
     * walk finds free.
     *
     * @param level       the level
     * @param pos         where the charge stands
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     * @return true where no reachable cell turns its block and no step leads off the goo
     */
    private static boolean stranded(Level level, BlockPos pos, SculkSpreader spreader, int sculkLayers) {
        GooServerState state = GooServerState.of(level);
        long now = level.getGameTime();
        if (state != null && state.creepFreeCells().isFree(level, pos, now)) {
            return false;
        }
        CreepReach.Walk walk = CreepReach.walk(level, pos, cell -> layersSpentAt(level, cell, spreader, sculkLayers) > 0);
        if (!walk.stranded() && state != null) {
            state.creepFreeCells().markFree(level, walk.visited(), now);
        }
        return walk.stranded();
    }

    /**
     * The layers a cell would spend turning the block under it to sculk.
     *
     * @param level       the level
     * @param cell        the cell
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     * @return the layers spent, 0 for a cell turning nothing or for anything but a Creep cell
     */
    private static int layersSpentAt(Level level, BlockPos cell, SculkSpreader spreader, int sculkLayers) {
        BlockState state = level.getBlockState(cell);
        if (!(state.getBlock() instanceof CreepCellBlock)) {
            return 0;
        }
        return CreepSpread.layersSpent(state.getValue(LAYERS),
                level.getBlockState(cell.below()).is(spreader.replaceableBlocks()), sculkLayers);
    }

    /**
     * Turns the block under a cell to sculk where the cell holds a block of
     * sculk's layers over ground sculk replaces, the cell resting on it lower
     * by those layers and gone where that empties it.
     *
     * @param level       the level
     * @param pos         the cell
     * @param spreader    the spreader whose replaceable tag names the ground sculk takes
     * @param sculkLayers the layers a block of sculk spends
     */
    private static void turnTheBlockUnder(Level level, BlockPos pos, SculkSpreader spreader, int sculkLayers) {
        int spent = layersSpentAt(level, pos, spreader, sculkLayers);
        if (spent == 0) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        int layers = state.getValue(LAYERS);
        BlockPos under = pos.below();
        level.setBlock(under, Blocks.SCULK.defaultBlockState(), UPDATE_ALL);
        level.setBlock(pos, layers > spent ? state.setValue(LAYERS, layers - spent)
                : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        level.playSound(null, under, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * Grows no veins from the goo: the charge runs through the slime itself.
     */
    @Override
    public boolean attemptSpreadVein(LevelAccessor level, BlockPos pos, BlockState state,
                                     @Nullable Collection<Direction> facings, boolean postProcess) {
        return false;
    }

    /**
     * Pours as any cell does, then, where the cell still stands, buds an edge on a period's tick, and keeps a tick
     * coming, a pending pour's or the next period's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        CreepStep.in(layingProgram(level)).ifPresent(creep -> {
            long now = level.getGameTime();
            if (creep.onPeriod(now)) {
                budAnEdge(level, pos, creep);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, creep.ticksToNextPeriod(now));
            }
        });
    }

    /**
     * Puffs the cell's spore particle off its top now and then, on the chance the step tunes, as mycelium puffs its
     * own; the client draws it alone.
     * operator ruling 2026-11-18: give them a random tick chance on these spores
     */
    @Override
    public void animateTick(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                            @NonNull RandomSource random) {
        CreepStep.in(layingProgram(level)).flatMap(creep -> creep.emissionFor(random.nextFloat(), pos))
                .flatMap(emission -> SimpleParticles.resolve(emission.particle()))
                .ifPresent(particle -> level.addParticle(particle, pos.getX() + random.nextDouble(),
                        pos.getY() + layersOf(state) / (double) MAX_LAYERS + SPORE_LIFT,
                        pos.getZ() + random.nextDouble(), 0, SPORE_SPEED, 0));
    }

    /**
     * Places the next bud CreepGrowth answers on an edge space and stands the cell lower by its layers, gone where
     * they were its last.
     * spores-goo-spends-on-edge-growth-and-grows-to-buds
     *
     * @param level the server level
     * @param pos   the cell
     * @param creep Creep's step
     */
    private void budAnEdge(ServerLevel level, BlockPos pos, CreepStep creep) {
        BlockState standing = level.getBlockState(pos);
        BlockState bud = creep.budState();
        CreepGrowth.Budding budding = CreepGrowth.nextBud(edges(level, pos, bud), level.getRandom().nextInt(),
                standing.getValue(LAYERS), creep.layersPerBud());
        budding.spot().ifPresent(spot -> {
            level.setBlock(spot, bud, UPDATE_ALL);
            level.setBlock(pos, budding.layersLeft() > 0 ? standing.setValue(LAYERS, budding.layersLeft())
                    : Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        });
    }

    /**
     * The spaces beside a cell, its four sides at its level and the four just above them, each read for whether it
     * holds nothing and whether a fungal bud can stand there.
     *
     * @param level the server level
     * @param pos   the cell
     * @param bud   the fungal bud's state, whose rule says where it can stand
     * @return the edge spaces
     */
    private static List<CreepGrowth.Edge> edges(ServerLevel level, BlockPos pos, BlockState bud) {
        return Direction.Plane.HORIZONTAL.stream()
                .flatMap(side -> Stream.of(pos.relative(side), pos.relative(side).above()))
                .map(space -> new CreepGrowth.Edge(space, level.getBlockState(space).isAir(),
                        bud.canSurvive(level, space)))
                .toList();
    }
}
