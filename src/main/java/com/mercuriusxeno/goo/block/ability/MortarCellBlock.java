package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.ability.program.MortarStep;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Rock's Mortar cell: the volume cell bound to Mortar's goo, Petrify's
 * effects left standing. Each tick a mob stands in it, the mob petrifies by
 * Petrify's own fill, as under Petrify's fog; on each tick its cadence falls
 * on, it hardens the block under it and the block behind each side it coats
 * one stage along Petrify's calcify map. Each block hardened costs a layer,
 * and each second a mob stands in it costs a layer; the goo is never turned
 * into anything else, and a cell losing its last layer is gone. The cell
 * credits the player who placed it for the statues it makes.
 * mortar-is-rocks-residual-a-petrification-and-hardening-goo
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 * standing-goo-thing-credits-whoever-placed-it
 */
public class MortarCellBlock extends VolumeCellBlock {

    /**
     * Creates a Mortar cell.
     *
     * @param properties the block properties
     * @param binding    the Mortar binding
     */
    public MortarCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * The layers of Mortar a block holds, for a later combo reading a cell
     * without reaching into it.
     *
     * @param level the level
     * @param pos   the block
     * @return the layers, 0 where the block holds no Mortar
     */
    public static int layersAt(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof MortarCellBlock ? layersOf(state) : 0;
    }

    /**
     * Whether a block holds Mortar lying on a block's sturdy top, as paint on
     * the ground does, rather than clinging to a wall or a ceiling alone.
     *
     * @param level the level
     * @param pos   the block
     * @return true where the block holds Mortar on a top
     */
    public static boolean liesOnATop(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof MortarCellBlock mortar && mortar.liesOn(level, pos);
    }

    /**
     * The Mortar cells joined to one through their faces, the one first.
     *
     * @param level the level
     * @param pos   the cell the network is read from
     * @param limit the most cells read
     * @return the network, empty where the block holds no Mortar
     */
    public static Set<BlockPos> networkAt(BlockGetter level, BlockPos pos, int limit) {
        return networkFrom(pos, at -> layersAt(level, at) > 0, limit);
    }

    /**
     * The cells joined to one through their faces, read breadth first so the
     * nearest come first, up to a limit.
     *
     * @param start    the cell the network is read from
     * @param inNetwork whether a block belongs to the network
     * @param limit    the most cells read
     * @return the network in the order it was reached, empty where the start is outside it
     */
    static Set<BlockPos> networkFrom(BlockPos start, Predicate<BlockPos> inNetwork, int limit) {
        Set<BlockPos> network = new LinkedHashSet<>();
        if (!inNetwork.test(start)) {
            return network;
        }
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start.immutable());
        network.add(start.immutable());
        while (!frontier.isEmpty() && network.size() < limit) {
            reachNeighbours(frontier.poll(), inNetwork, limit, network, frontier);
        }
        return network;
    }

    /**
     * Joins to a network each face neighbour of one of its cells that
     * belongs to it and is not yet held, queueing each for its own neighbours.
     *
     * @param at        the cell whose neighbours are read
     * @param inNetwork whether a block belongs to the network
     * @param limit     the most cells read
     * @param network   the cells joined so far
     * @param frontier  the cells whose neighbours wait to be read
     */
    private static void reachNeighbours(BlockPos at, Predicate<BlockPos> inNetwork, int limit, Set<BlockPos> network,
                                        Deque<BlockPos> frontier) {
        for (Direction side : Direction.values()) {
            BlockPos next = at.relative(side);
            if (network.size() < limit && inNetwork.test(next) && network.add(next)) {
                frontier.add(next);
            }
        }
    }

    /**
     * The cell of a network a giga drill drains next: the one farthest from
     * the bored cell through the network's faces, so the network drains from
     * its edges inward and the bored cell last.
     * boring-through-rock-goo-gives-a-giga-drill-that-drains-it
     *
     * @param network the cells of the network still holding Mortar
     * @param bored   the cell the drill passes through
     * @return the cell to drain, empty where the network is spent
     */
    public static Optional<BlockPos> nextToDrain(Set<BlockPos> network, BlockPos bored) {
        Set<BlockPos> nearestFirst = networkFrom(bored, network::contains, network.size());
        return nearestFirst.stream().reduce((nearer, farther) -> farther);
    }

    /**
     * Takes one layer off a Mortar cell, giving no goo back, the cell gone
     * where it held its last.
     * boring-through-rock-goo-gives-a-giga-drill-that-drains-it
     *
     * @param level the server level
     * @param pos   the cell
     */
    public static void drainALayer(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof MortarCellBlock) {
            int left = layersAfterCosts(layersOf(state), 1, 0);
            level.setBlock(pos, left == 0 ? Blocks.AIR.defaultBlockState() : state.setValue(LAYERS, left), UPDATE_ALL);
        }
    }

    /**
     * The layers a cell holds after what it spent: one for each block it
     * hardened and one for each second a mob stood in it.
     * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
     *
     * @param layers          the layers before
     * @param hardened        the blocks hardened
     * @param standingSeconds the seconds a mob stood in the cell
     * @return the layers left, 0 where the costs took them all
     */
    public static int layersAfterCosts(int layers, int hardened, int standingSeconds) {
        return Math.max(0, layers - hardened - standingSeconds);
    }

    /**
     * Pours as any cell does, then, where the cell still stands, hardens on a
     * tick its cadence falls on and keeps a tick coming, a pending pour's or
     * the next hardening's.
     */
    @Override
    protected void tick(@NonNull BlockState state, @NonNull ServerLevel level, @NonNull BlockPos pos,
                        @NonNull RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getBlockState(pos).is(this)) {
            return;
        }
        MortarStep.in(layingProgram(level)).ifPresent(mortar -> {
            if (CellReach.actsAt(level.getGameTime(), mortar.ticks())) {
                spend(level, pos, harden(level, pos, level.getBlockState(pos), mortar), 0);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, CellReach.ticksToNextAct(level.getGameTime(), mortar.ticks()));
            }
        });
    }

    /**
     * Petrifies a mob standing on the cell, as on any cell's top.
     */
    @Override
    public void stepOn(@NonNull Level level, @NonNull BlockPos pos, @NonNull BlockState state,
                       @NonNull Entity entity) {
        petrifyWhatStands(level, pos, entity);
        super.stepOn(level, pos, state, entity);
    }

    /**
     * Petrifies a mob standing in the cell's goo.
     */
    @Override
    protected void entityInside(@NonNull BlockState state, @NonNull Level level, @NonNull BlockPos pos,
                                @NonNull Entity entity, @NonNull InsideBlockEffectApplier effects,
                                boolean pastEdges) {
        petrifyWhatStands(level, pos, entity);
        super.entityInside(state, level, pos, entity, effects, pastEdges);
    }

    /**
     * Petrifies a mob standing in the cell one tick's fill through Petrify's
     * own step, once a tick however many hooks report it, and spends a layer
     * on each tick a whole second falls on while it stands there.
     * operator ruling 2026-11-18: petrify at Petrify's pace; one layer per second a mob stands
     *
     * @param level  the level
     * @param pos    the cell
     * @param entity the entity touching the cell
     */
    private void petrifyWhatStands(Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof Mob mob)
                || !petrifiesThisTick(server, pos, mob)) {
            return;
        }
        MortarStep.in(layingProgram(level)).ifPresent(mortar -> {
            boolean paysASecond = CellReach.actsAt(server.getGameTime(), mortar.standingTicksPerLayer());
            PetrifyStep.petrifyOneStep(server, mob, mortar.fillFor(mob), creditedPlayerAt(server, pos));
            if (paysASecond) {
                spend(server, pos, 0, 1);
            }
        });
    }

    /**
     * Whether a mob takes this tick's petrify from the cell: it lives, it
     * stands in the cell, and no hook has filled its gauge this tick.
     *
     * @param level the server level
     * @param pos   the cell
     * @param mob   the mob touching the cell
     * @return true where the mob petrifies this tick
     */
    private static boolean petrifiesThisTick(ServerLevel level, BlockPos pos, Mob mob) {
        boolean filledThisTick = mob.getData(GooAttachments.PETRIFICATION).lastFill() == level.getGameTime();
        return mob.isAlive() && !filledThisTick && CellReach.standsIn(pos, mob.blockPosition(), mob.getOnPos());
    }

    /**
     * Stands the cell lower by what it spent, gone where it spent every layer.
     * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
     *
     * @param level           the server level
     * @param pos             the cell
     * @param hardened        the blocks it hardened
     * @param standingSeconds the seconds a mob stood in it
     */
    private void spend(ServerLevel level, BlockPos pos, int hardened, int standingSeconds) {
        BlockState state = level.getBlockState(pos);
        if (hardened + standingSeconds == 0 || !state.is(this)) {
            return;
        }
        int left = layersAfterCosts(state.getValue(LAYERS), hardened, standingSeconds);
        level.setBlock(pos, left == 0 ? Blocks.AIR.defaultBlockState() : state.setValue(LAYERS, left), UPDATE_ALL);
    }

    /**
     * Steps each block the cell reaches one stage along Mortar's map, Petrify's calcify map.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param state  the cell's state
     * @param mortar Mortar's step
     * @return the blocks hardened
     */
    private static int harden(ServerLevel level, BlockPos pos, BlockState state, MortarStep mortar) {
        Optional<BlockMap> map = BlockMaps.get(mortar.map());
        if (map.isEmpty()) {
            return 0;
        }
        int hardened = 0;
        for (BlockPos block : CellReach.reachedBlocks(pos, side -> coatOn(state, side) != SideCoat.BARE)) {
            Optional<Block> next = map.get().next(level.getBlockState(block).getBlock());
            if (next.isPresent()) {
                level.setBlockAndUpdate(block, next.get().defaultBlockState());
                hardened++;
            }
        }
        return hardened;
    }

    /**
     * A Mortar cell's petrify makes statues, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }
}
