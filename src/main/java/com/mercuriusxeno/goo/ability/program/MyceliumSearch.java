package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The direct search answering whether a path of mycelial blocks joins a
 * destination to any block a caster touches: a breadth-first walk over the
 * blocks around each block it reaches, bounded to the blocks within a range
 * of the destination, reading every block it meets from the world.
 * Blocks join at faces, edges and corners, so sculk carpeting a slope stays
 * one network while one stone block in a flat line parts it
 * (operator ruling 2026-11-19).
 * transmit-reaches-only-connected-mycelial-blocks-you-touch
 */
final class MyceliumSearch {

    private static final int AROUND = 1;

    private final Predicate<BlockPos> mycelial;
    private final BlockPos destination;
    private final double reachSqr;
    private final Set<BlockPos> seen = new HashSet<>();
    private final Deque<BlockPos> next = new ArrayDeque<>();

    private MyceliumSearch(Predicate<BlockPos> mycelial, BlockPos destination, double range) {
        this.mycelial = mycelial;
        this.destination = destination;
        this.reachSqr = range * range;
    }

    /**
     * Whether a path of mycelial blocks joins the destination to any origin.
     *
     * @param mycelial    whether a place holds a mycelial block
     * @param origins     the blocks the caster touches
     * @param destination the aimed block
     * @param range       how far from the destination, in blocks, the path may run
     * @return true where a path joins them
     */
    static boolean connects(Predicate<BlockPos> mycelial, Collection<BlockPos> origins, BlockPos destination,
                            double range) {
        MyceliumSearch search = new MyceliumSearch(mycelial, destination, range);
        origins.forEach(search::visit);
        return search.walksToTheDestination();
    }

    private boolean walksToTheDestination() {
        while (!next.isEmpty()) {
            BlockPos at = next.poll();
            if (at.equals(destination)) {
                return true;
            }
            for (BlockPos beside : BlockPos.betweenClosed(at.offset(-AROUND, -AROUND, -AROUND),
                    at.offset(AROUND, AROUND, AROUND))) {
                visit(beside);
            }
        }
        return false;
    }

    /**
     * Reads a place the walk meets once, and queues it where it holds a
     * mycelial block within the range.
     *
     * @param pos the place, possibly a mutable cursor
     */
    private void visit(BlockPos pos) {
        if (pos.distSqr(destination) > reachSqr || seen.contains(pos)) {
            return;
        }
        BlockPos held = pos.immutable();
        seen.add(held);
        if (mycelial.test(held)) {
            next.add(held);
        }
    }
}
