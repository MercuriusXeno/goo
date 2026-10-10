package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.block.ability.RailLine;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Glow's reflector prisms and the light rails between them. Each reflector
 * links to every other reflector in loaded chunks that stands on one of its
 * axes, never on a diagonal, with only air, or rail, on the straight line
 * between them, and the line fills with light rail.
 * The reflectors linked one to the next form a network, and every rail in
 * it carries the brightest light any of its reflectors reads at its own
 * cell; a rail's own light reaches a reflector one level dimmer, so a
 * network cannot hold itself lit and settles to the light it is given.
 * A line no longer clear loses its rail.
 * decision reflector-rails-carry-the-brightest-light
 * operator ruling 2026-10-09: reflectors link orthogonally, never diagonally
 */
public final class Reflectors {

    /** The reflectors each level holds, known as their programs run. */
    private static final Map<ServerLevel, Set<BlockPos>> BY_LEVEL = Collections.synchronizedMap(new WeakHashMap<>());
    /** The least light a rail carries, so a linked rail always shows. */
    static final int DIMMEST_RAIL = 1;

    private Reflectors() {
    }

    /**
     * Refreshes a reflector: records it, drops the reflectors gone, relinks
     * it to every reflector in clear line, clears the rail off its broken
     * links, and lights its whole network's rails at the network's brightest.
     *
     * @param level the server level
     * @param self  the reflector refreshing
     */
    public static void refresh(ServerLevel level, BlockPos self) {
        Set<BlockPos> known = BY_LEVEL.computeIfAbsent(level, ignored -> new HashSet<>());
        known.add(self.immutable());
        known.removeIf(pos -> level.isLoaded(pos) && !isReflector(level, pos));
        if (!(level.getBlockEntity(self) instanceof PrismBlockEntity prism)) {
            return;
        }
        List<BlockPos> partners = partnersOf(level, self, known);
        clearBrokenLinks(level, self, prism.getLinks(), partners);
        Set<BlockPos> network = networkOf(level, self, known);
        int light = networkLight(level, network);
        for (BlockPos member : network) {
            lightMember(level, member, member.equals(self) ? partners : partnersOf(level, member, known), light);
        }
    }

    private static void clearBrokenLinks(ServerLevel level, BlockPos self, List<BlockPos> linked,
                                         List<BlockPos> partners) {
        for (BlockPos broken : linked) {
            if (!partners.contains(broken)) {
                clearRail(level, RailLine.between(self, broken));
            }
        }
    }

    private static void lightMember(ServerLevel level, BlockPos member, List<BlockPos> links, int light) {
        for (BlockPos partner : links) {
            layRail(level, RailLine.between(member, partner), light);
        }
        if (level.getBlockEntity(member) instanceof PrismBlockEntity linked) {
            linked.setLinks(links, light);
        }
    }

    /**
     * Forgets every reflector a level held, as the server stops.
     *
     * @param level the level
     */
    public static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    private static boolean isReflector(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PrismBlockEntity prism && prism.isReflector();
    }

    /**
     * The reflectors a reflector links to: every other loaded one on one of its axes with a clear line between.
     *
     * @param level the server level
     * @param from  the reflector
     * @param known the reflectors the level holds
     * @return its partners
     */
    private static List<BlockPos> partnersOf(ServerLevel level, BlockPos from, Set<BlockPos> known) {
        List<BlockPos> partners = new ArrayList<>();
        for (BlockPos other : known) {
            if (onOneAxis(from, other) && level.isLoaded(other) && isClear(level, RailLine.between(from, other))) {
                partners.add(other);
            }
        }
        return partners;
    }

    /**
     * Whether two distinct cells stand on one axis: they share two of their
     * three coordinates, so the line between them runs straight along it.
     *
     * @param from the one cell
     * @param to   the other cell
     * @return true when the cells differ along exactly one axis
     */
    static boolean onOneAxis(BlockPos from, BlockPos to) {
        int differingAxes = (from.getX() == to.getX() ? 0 : 1) + (from.getY() == to.getY() ? 0 : 1)
                + (from.getZ() == to.getZ() ? 0 : 1);
        return differingAxes == 1;
    }

    private static Set<BlockPos> networkOf(ServerLevel level, BlockPos start, Set<BlockPos> known) {
        Set<BlockPos> network = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        frontier.add(start);
        network.add(start);
        while (!frontier.isEmpty()) {
            for (BlockPos partner : partnersOf(level, frontier.poll(), known)) {
                if (network.add(partner)) {
                    frontier.add(partner);
                }
            }
        }
        return network;
    }

    /**
     * The light a network's rails carry: the brightest light any of its
     * reflectors reads at its own cell, never below the dimmest rail.
     *
     * @param level   the server level
     * @param network the network's reflectors
     * @return the light level
     */
    private static int networkLight(ServerLevel level, Set<BlockPos> network) {
        int brightest = DIMMEST_RAIL;
        for (BlockPos member : network) {
            brightest = Math.max(brightest, level.getMaxLocalRawBrightness(member));
        }
        return brightest;
    }

    private static boolean isClear(ServerLevel level, List<BlockPos> line) {
        for (BlockPos cell : line) {
            if (!level.isLoaded(cell)) {
                return false;
            }
            BlockState state = level.getBlockState(cell);
            if (!state.isAir() && !state.is(GooBlocks.LIGHT_RAIL.get())) {
                return false;
            }
        }
        return true;
    }

    private static void layRail(ServerLevel level, List<BlockPos> line, int light) {
        BlockState rail = GooBlocks.LIGHT_RAIL.get().defaultBlockState().setValue(LightRailBlock.LEVEL, light);
        for (BlockPos cell : line) {
            if (!level.getBlockState(cell).equals(rail)) {
                level.setBlock(cell, rail, Block.UPDATE_ALL);
            }
        }
    }

    private static void clearRail(ServerLevel level, List<BlockPos> line) {
        for (BlockPos cell : line) {
            if (level.isLoaded(cell) && level.getBlockState(cell).is(GooBlocks.LIGHT_RAIL.get())) {
                level.removeBlock(cell, false);
            }
        }
    }
}
