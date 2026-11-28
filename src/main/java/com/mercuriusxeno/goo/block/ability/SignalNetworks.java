package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * The highest layer count of each Signal network, a network being the Signal
 * cells joined face to face, held per server level by each cell's position
 * so a cell's signal is read without walking its network. A cell missing
 * from the hold walks its network once and holds the answer for every cell
 * of it; a cell placed, changed or removed walks the networks it touches
 * again, and each cell whose signal changes updates its neighbours, so what
 * it powers reads the new strength at once. The hold is never saved: a
 * level loaded anew walks each network the first time a cell of it is read.
 * pulse-goo-network-signals-at-its-highest-layer
 */
final class SignalNetworks {

    private static final Map<ServerLevel, Map<BlockPos, Integer>> BY_LEVEL =
            Collections.synchronizedMap(new WeakHashMap<>());

    private SignalNetworks() {
    }

    /**
     * The highest layer count in the network of the Signal cell at a place,
     * read from the hold on a server level and walked anew anywhere else.
     *
     * @param level the level
     * @param pos   the Signal cell
     * @param cell  the Signal cell block
     * @return the network's highest layer count
     */
    static int highestAt(BlockGetter level, BlockPos pos, Block cell) {
        if (!(level instanceof ServerLevel server)) {
            return highestOf(level, walk(level, pos, cell, at -> true));
        }
        Map<BlockPos, Integer> held = heldIn(server);
        Integer highest = held.get(pos);
        if (highest != null) {
            return highest;
        }
        Set<BlockPos> network = walk(server, pos, cell, at -> true);
        int walked = highestOf(server, network);
        network.forEach(member -> held.put(member, walked));
        return walked;
    }

    /**
     * Walks again every network a Signal cell's change touches: the cell's
     * own where it still stands, and each network a face neighbour stands in,
     * so networks joined or split by the change each hold their own highest.
     *
     * @param level   the server level
     * @param pos     the cell that was placed, changed or removed
     * @param cell    the Signal cell block
     * @param removed whether the cell at the place is gone
     */
    static void refreshAround(ServerLevel level, BlockPos pos, Block cell, boolean removed) {
        Map<BlockPos, Integer> held = heldIn(level);
        Predicate<BlockPos> standing = at -> !removed || !at.equals(pos);
        Set<BlockPos> walked = new HashSet<>();
        if (removed) {
            held.remove(pos);
        } else {
            refreshNetwork(level, pos, cell, standing, walked);
        }
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!walked.contains(next) && isCell(level, next, cell)) {
                refreshNetwork(level, next, cell, standing, walked);
            }
        }
    }

    /**
     * Walks the network from one cell, holds its highest layer count for every
     * cell of it, then updates the neighbours of each cell whose held count
     * changed, once the whole network holds the new count, so a neighbour
     * reading the signal as it updates reads the new count from any cell.
     *
     * @param level    the server level
     * @param start    a cell of the network
     * @param cell     the Signal cell block
     * @param standing whether a place's cell still stands, false for one being removed
     * @param walked   the cells walked so far this refresh, joined by this network's
     */
    private static void refreshNetwork(ServerLevel level, BlockPos start, Block cell, Predicate<BlockPos> standing,
                                       Set<BlockPos> walked) {
        Set<BlockPos> network = walk(level, start, cell, standing);
        int highest = highestOf(level, network);
        Map<BlockPos, Integer> held = heldIn(level);
        List<BlockPos> changed = network.stream()
                .filter(member -> !Objects.equals(held.put(member, highest), highest)).toList();
        changed.forEach(member -> level.updateNeighborsAt(member, cell));
        walked.addAll(network);
    }

    private static Set<BlockPos> walk(BlockGetter level, BlockPos start, Block cell, Predicate<BlockPos> standing) {
        return MortarCellBlock.networkFrom(start, at -> standing.test(at) && isCell(level, at, cell),
                Integer.MAX_VALUE);
    }

    private static int highestOf(BlockGetter level, Set<BlockPos> network) {
        return network.stream().mapToInt(at -> VolumeCellBlock.layersOf(level.getBlockState(at))).max().orElse(0);
    }

    /**
     * Whether a place holds a Signal cell, read only where its chunk is
     * loaded, so a walk never loads a chunk.
     *
     * @param level the level
     * @param at    the place
     * @param cell  the Signal cell block
     * @return true where a loaded place holds a Signal cell
     */
    private static boolean isCell(BlockGetter level, BlockPos at, Block cell) {
        boolean loaded = !(level instanceof Level loadable) || loadable.isLoaded(at);
        return loaded && level.getBlockState(at).is(cell);
    }

    private static Map<BlockPos, Integer> heldIn(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, ignored -> new HashMap<>());
    }
}
