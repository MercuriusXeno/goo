package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.block.ability.CellPlacers;
import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.ability.RailLine;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Reflectors, each a {@link LightRailEnd}, and the light rails between them.
 * Each reflector links to every other reflector in loaded chunks that stands
 * on one of its axes, never on a diagonal, with an open lens on each end
 * facing the other and only air, or rail, on the straight line between them,
 * and the line fills with light rail.
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
        if (!(level.getBlockEntity(self) instanceof LightRailEnd end)) {
            return;
        }
        List<BlockPos> partners = partnersOf(level, self, known);
        clearBrokenLinks(level, self, end.getLinks(), partners);
        Set<BlockPos> network = networkOf(level, self, known);
        int light = networkLight(level, network);
        // rail this refresh newly lays credits the player who set the refreshing reflector
        // standing-goo-thing-credits-whoever-placed-it
        Laying laying = new Laying(light, end.railPlacer());
        for (BlockPos member : network) {
            lightMember(level, member, member.equals(self) ? partners : partnersOf(level, member, known), laying);
        }
    }

    /**
     * What a refresh lays its rails with: the network's light and the player
     * whose goo set the refreshing reflector, whom a rail it newly lays credits.
     * operator ruling 2026-11-17: when two players place, the last player overrides
     *
     * @param light  the light the rails carry
     * @param placer the player a newly laid rail credits, or null for none
     */
    private record Laying(int light, @Nullable UUID placer) {
    }

    private static void clearBrokenLinks(ServerLevel level, BlockPos self, List<BlockPos> linked,
                                         List<BlockPos> partners) {
        for (BlockPos broken : linked) {
            if (!partners.contains(broken)) {
                clearRail(level, RailLine.between(self, broken));
            }
        }
    }

    private static void lightMember(ServerLevel level, BlockPos member, List<BlockPos> links, Laying laying) {
        for (BlockPos partner : links) {
            layRail(level, RailLine.between(member, partner), laying);
        }
        if (level.getBlockEntity(member) instanceof LightRailEnd linked) {
            linked.setLinks(links, laying.light());
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
        return level.getBlockEntity(pos) instanceof LightRailEnd;
    }

    /**
     * The reflectors a reflector links to: every other loaded one on one of its axes, open lens facing open lens,
     * with a clear line between, which makes it the next reflector each open lens meets.
     *
     * @param level the server level
     * @param from  the reflector
     * @param known the reflectors the level holds
     * @return its partners
     */
    private static List<BlockPos> partnersOf(ServerLevel level, BlockPos from, Set<BlockPos> known) {
        List<BlockPos> partners = new ArrayList<>();
        for (BlockPos other : known) {
            if (onOneAxis(from, other) && level.isLoaded(other) && lensesFace(level, from, other)
                    && isClear(level, RailLine.between(from, other))) {
                partners.add(other);
            }
        }
        return partners;
    }

    /**
     * Whether two reflectors on one axis each hold an open lens toward the other.
     * decision reflector-rails-carry-the-brightest-light
     *
     * @param level the server level
     * @param from  the one reflector
     * @param to    the other, on one of its axes
     * @return true when both lenses between them are open
     */
    private static boolean lensesFace(ServerLevel level, BlockPos from, BlockPos to) {
        Direction ahead = toward(from, to);
        return level.getBlockEntity(from) instanceof LightRailEnd near && near.lensOpen(ahead)
                && level.getBlockEntity(to) instanceof LightRailEnd far && far.lensOpen(ahead.getOpposite());
    }

    /**
     * The side of one cell another on one of its axes lies toward.
     *
     * @param from the one cell
     * @param to   the other, differing from it along exactly one axis
     * @return the side facing it
     */
    static Direction toward(BlockPos from, BlockPos to) {
        BlockPos offset = to.subtract(from);
        return Direction.getApproximateNearest(offset.getX(), offset.getY(), offset.getZ());
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

    /**
     * Lays rail along a line at the laying's light; a cell that held no rail
     * before credits the laying's player, and a cell already rail keeps the
     * player who laid it.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param level  the server level
     * @param line   the cells between two linked reflectors
     * @param laying the light and the player the rail is laid with
     */
    private static void layRail(ServerLevel level, List<BlockPos> line, Laying laying) {
        BlockState rail = GooBlocks.LIGHT_RAIL.get().defaultBlockState().setValue(LightRailBlock.LEVEL, laying.light());
        for (BlockPos cell : line) {
            BlockState standing = level.getBlockState(cell);
            if (standing.equals(rail)) {
                continue;
            }
            level.setBlock(cell, rail, Block.UPDATE_ALL);
            if (laying.placer() != null && !standing.is(GooBlocks.LIGHT_RAIL.get())) {
                CellPlacers.of(level).place(cell, laying.placer());
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
