package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.blockmap.BlockMap;
import com.mercuriusxeno.goo.ability.blockmap.BlockMaps;
import com.mercuriusxeno.goo.ability.program.MortarStep;
import com.mercuriusxeno.goo.ability.program.PetrifyStep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Rock's Mortar cell: the volume cell bound to Mortar's goo, Petrify's
 * effects left standing. On each tick its cadence falls on it petrifies each
 * mob standing in it one step, as Petrify's fog does each held tick, and
 * hardens the block under it and the block behind each side it coats one
 * stage along Petrify's calcify map. Each petrifying step and each block
 * hardened rolls the step's chance to take a layer; the goo is never turned
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
     * Pours as any cell does, then, where the cell still stands, petrifies
     * and hardens on a tick its cadence falls on and keeps a tick coming, a
     * pending pour's or the next act's.
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
                petrifyAndHarden(level, pos, mortar);
            }
            if (level.getBlockState(pos).is(this) && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                level.scheduleTick(pos, this, CellReach.ticksToNextAct(level.getGameTime(), mortar.ticks()));
            }
        });
    }

    /**
     * Hardens what the cell lies on, petrifies what stands in it, and stands
     * the cell lower by the layers its rolls took, gone where they took them all.
     * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
     *
     * @param level  the server level
     * @param pos    the cell
     * @param mortar Mortar's step
     */
    private void petrifyAndHarden(ServerLevel level, BlockPos pos, MortarStep mortar) {
        int rolls = harden(level, pos, level.getBlockState(pos), mortar) + petrify(level, pos, mortar);
        BlockState after = level.getBlockState(pos);
        if (rolls == 0 || !after.is(this)) {
            return;
        }
        int layers = after.getValue(LAYERS);
        int left = LayerDeplete.layersAfterRolls(layers, rolls, level.getRandom()::nextDouble,
                mortar.depleteChance());
        if (left == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        } else if (left != layers) {
            level.setBlock(pos, after.setValue(LAYERS, left), UPDATE_ALL);
        }
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
     * Petrifies each mob standing in the cell one step, through Petrify's own step.
     *
     * @param level  the server level
     * @param pos    the cell
     * @param mortar Mortar's step
     * @return the mobs petrified
     */
    private int petrify(ServerLevel level, BlockPos pos, MortarStep mortar) {
        Entity placer = creditedPlayerAt(level, pos);
        int petrified = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(pos).expandTowards(0, 1, 0),
                mob -> mob.isAlive() && CellReach.standsIn(pos, mob.blockPosition(), mob.getOnPos()))) {
            PetrifyStep.petrifyOneStep(level, mob, mortar.fillFor(mob), placer);
            petrified++;
        }
        return petrified;
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
