package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The slime pour rule, pure of any level: a slime cell taller than a side
 * neighbour pours one layer a tick into it, and off a ledge downward, until
 * it stands level, as a fluid levels out.
 * slime-is-a-layered-cell-that-levels-out
 */
public final class SlimeFlow {

    /**
     * How many layers lower a side must stand to take a pour; a gap of one
     * stands level, as it would only pour back.
     */
    static final int POUR_GAP = 2;

    /** What a side neighbour's cell can do with a poured layer. */
    enum Footing {
        /** A slime cell, or air over a sturdy top: it holds the layer where it stands. */
        HOLDS,
        /** Air over air or over unfilled slime: the layer falls through it to the first top below. */
        LEDGE,
        /** Anything else: it takes no layer. */
        BLOCKED
    }

    /**
     * One side neighbour of a pouring cell.
     *
     * @param direction the side it stands on
     * @param layers    the coat layers it holds, 0 for air
     * @param footing   what it can do with a poured layer
     */
    record Side(Direction direction, int layers, Footing footing) {

        static Side holds(Direction direction, int layers) {
            return new Side(direction, layers, Footing.HOLDS);
        }

        static Side ledge(Direction direction) {
            return new Side(direction, 0, Footing.LEDGE);
        }

        static Side blocked(Direction direction) {
            return new Side(direction, 0, Footing.BLOCKED);
        }
    }

    private SlimeFlow() {
    }

    /**
     * Which side neighbour takes one of a cell's layers this tick: an open
     * ledge before any neighbour, else the lowest neighbour holding at least
     * two layers fewer, the first listed among equals. A cell never pours
     * its last layer, so a one-layer film stays where it was laid, dripping
     * at its open edges.
     * slime-is-a-layered-cell-that-levels-out
     * coat-is-a-green-slime-film-over-the-blocks-own-texture
     *
     * @param layers the coat layers the pouring cell holds
     * @param sides  its side neighbours
     * @return the side that takes a layer, empty where the cell stands level
     */
    static Optional<Direction> pour(int layers, List<Side> sides) {
        List<Side> takers = sides.stream().filter(side -> takes(layers, side)).toList();
        Optional<Side> ledge = takers.stream().filter(side -> side.footing() == Footing.LEDGE).findFirst();
        return ledge.or(() -> takers.stream().min(Comparator.comparingInt(Side::layers))).map(Side::direction);
    }

    /**
     * Whether a side can take a layer from a cell: it is open and stands at
     * least POUR_GAP layers below, a ledge standing at none.
     *
     * @param layers the coat layers the pouring cell holds
     * @param side   the side
     * @return true where the side takes a layer
     */
    private static boolean takes(int layers, Side side) {
        return side.footing() != Footing.BLOCKED && side.layers() <= layers - POUR_GAP;
    }
}
