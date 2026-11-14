package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.Direction;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The pour rule of a layered cell, pure of any level: a cell taller than a
 * side neighbour pours one whole layer into it, and off a ledge downward,
 * until it stands level, as a fluid levels out; the giver loses the layer the
 * taker gains, and the ticks between pours follow the goo type's viscosity.
 * slime-is-a-layered-cell-that-levels-out
 * spread-decreases-the-cell-it-leaves
 */
public final class SlimeFlow {

    /**
     * How many layers lower a side must stand to take a pour; a gap of one
     * stands level, as it would only pour back.
     */
    static final int POUR_GAP = 2;
    /**
     * The viscosity one tick between pours stands for: weird's 1000 waits
     * three ticks, kinetic's 800 two.
     */
    static final int VISCOSITY_PER_POUR_TICK = 400;
    /** Water's viscosity, the pace of a cell whose goo type's definition does not load. */
    static final int WATER_VISCOSITY = 1000;

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
     * Which side neighbour takes one of a cell's layers this pour: an open
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
     * What a pour leaves its two cells holding.
     *
     * @param giver the layers the pouring cell holds after the pour
     * @param taker the layers the cell taking the layer holds after it
     */
    record Poured(int giver, int taker) {
    }

    /**
     * A pour of one whole layer: the giver loses it and the taker gains it.
     * spread-decreases-the-cell-it-leaves
     *
     * @param giver the layers the pouring cell holds
     * @param taker the layers the cell taking the layer holds, 0 for an open cell
     * @return what the two cells hold after the pour
     */
    static Poured poured(int giver, int taker) {
        return new Poured(giver - 1, taker + 1);
    }

    /**
     * The ticks a cell waits between pours: its goo type's viscosity, on the
     * scale where water is 1000 and higher is thicker, over the viscosity a
     * tick of waiting stands for, rounded up, so a thinner goo pours sooner;
     * at least one tick.
     * spread-decreases-the-cell-it-leaves
     *
     * @param viscosity the goo type's viscosity
     * @return the ticks between pours
     */
    static int pourTicks(int viscosity) {
        return Math.max(1, (int) Math.ceil((double) viscosity / VISCOSITY_PER_POUR_TICK));
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
