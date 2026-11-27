package com.mercuriusxeno.goo.lunar;

import java.util.ArrayList;
import java.util.List;

/**
 * The little mini structures the warrens hold: small buried remains built from the alien structure family, each a
 * self-contained layout of a handful of blocks around one floor spot, and none of them one block repeated. Each layout
 * names the part it lays, not a block, so a test reads the layouts without the game's registries.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public final class WarrenRuinShapes {

    /** A part a ruin lays: the four alien structure family members, and the monolith marker free to stand in them. */
    public enum Part {
        /** The family's plain block. */
        PLAIN,
        /** The family's pillar, standing upright. */
        PILLAR,
        /** The family's pillar, lying along the shape's x. */
        PILLAR_LYING,
        /** The family's glyph block. */
        GLYPH,
        /** The family's tile. */
        TILE,
        /** A monolith marker, which stands only where it rests on a block under open air. */
        MARKER;

        /**
         * The family member this part lays, the marker laying none.
         *
         * @return the family member, or null for the marker
         */
        public Part member() {
            return switch (this) {
                case PILLAR_LYING -> PILLAR;
                case MARKER -> null;
                default -> this;
            };
        }
    }

    /**
     * One block of a ruin, at an offset from the floor spot: dy 0 is the first open block over the tunnel floor and dy
     * -1 is the floor itself.
     *
     * @param dx   the offset along x
     * @param dy   the offset up
     * @param dz   the offset along z
     * @param part the part laid there
     */
    public record Placement(int dx, int dy, int dz, Part part) {
    }

    /**
     * A ruin's layout.
     *
     * @param name   what the ruin is
     * @param blocks each block it lays
     */
    public record Shape(String name, List<Placement> blocks) {

        /**
         * The lowest offset up the shape lays at.
         *
         * @return the lowest dy
         */
        public int lowest() {
            return blocks.stream().mapToInt(Placement::dy).min().orElse(0);
        }

        /**
         * The highest offset up the shape lays at.
         *
         * @return the highest dy
         */
        public int highest() {
            return blocks.stream().mapToInt(Placement::dy).max().orElse(0);
        }
    }

    /** How far a square reaches from the spot, each way. */
    private static final int SQUARE_REACH = 1;

    /** A pillar on a patch of tiled floor, capped with a glyph block. */
    static final Shape STANDING_PILLAR = new Shape("standing_pillar", concat(
            square(-1, Part.TILE),
            List.of(at(0, 0, 0, Part.PILLAR), at(0, 1, 0, Part.PILLAR), at(0, 2, 0, Part.GLYPH))));

    /** An arch: two pillar legs under a plain lintel with a glyph keystone. */
    static final Shape ARCH = new Shape("arch", List.of(
            at(-1, 0, 0, Part.PILLAR), at(-1, 1, 0, Part.PILLAR), at(1, 0, 0, Part.PILLAR), at(1, 1, 0, Part.PILLAR),
            at(-1, 2, 0, Part.PLAIN), at(0, 2, 0, Part.GLYPH), at(1, 2, 0, Part.PLAIN)));

    /** A broken wall of plain blocks at uneven heights, one glyph set into it. */
    static final Shape BROKEN_WALL = new Shape("broken_wall", List.of(
            at(-2, 0, 0, Part.PLAIN), at(-2, 1, 0, Part.PLAIN), at(-1, 0, 0, Part.PLAIN), at(-1, 1, 0, Part.PLAIN),
            at(-1, 2, 0, Part.PLAIN), at(0, 0, 0, Part.PLAIN), at(0, 1, 0, Part.GLYPH), at(1, 0, 0, Part.PLAIN),
            at(1, 1, 0, Part.PLAIN), at(2, 0, 0, Part.PLAIN)));

    /** A plain plinth ringed by tiles, a monolith marker standing on it. */
    static final Shape PLINTH = new Shape("plinth", concat(
            square(-1, Part.TILE),
            List.of(at(0, 0, 0, Part.PLAIN), at(0, 1, 0, Part.MARKER))));

    /** A ragged patch of tiled floor with glyph blocks set into it. */
    static final Shape FLOOR_PATCH = new Shape("floor_patch", List.of(
            at(-1, -1, -1, Part.TILE), at(0, -1, -1, Part.GLYPH), at(1, -1, -1, Part.TILE),
            at(-1, -1, 0, Part.TILE), at(0, -1, 0, Part.TILE), at(1, -1, 0, Part.GLYPH), at(2, -1, 0, Part.TILE),
            at(0, -1, 1, Part.TILE), at(1, -1, 1, Part.TILE)));

    /** A fallen column lying on the floor, its glyph capital at one end and a plain stump at the other. */
    static final Shape FALLEN_COLUMN = new Shape("fallen_column", List.of(
            at(-2, 0, 0, Part.GLYPH), at(-1, 0, 0, Part.PILLAR_LYING), at(0, 0, 0, Part.PILLAR_LYING),
            at(1, 0, 0, Part.PILLAR_LYING), at(2, 0, 0, Part.PLAIN), at(2, 1, 0, Part.PLAIN)));

    /** An altar: a glyph block between two short pillars, on a strip of tiles. */
    static final Shape ALTAR = new Shape("altar", List.of(
            at(-1, -1, 0, Part.TILE), at(0, -1, 0, Part.TILE), at(1, -1, 0, Part.TILE),
            at(-1, 0, 0, Part.PILLAR), at(0, 0, 0, Part.GLYPH), at(1, 0, 0, Part.PILLAR)));

    /** Every ruin shape the warrens hold. */
    public static final List<Shape> ALL = List.of(STANDING_PILLAR, ARCH, BROKEN_WALL, PLINTH, FLOOR_PATCH,
            FALLEN_COLUMN, ALTAR);

    private WarrenRuinShapes() {
    }

    private static Placement at(int dx, int dy, int dz, Part part) {
        return new Placement(dx, dy, dz, part);
    }

    /**
     * A three by three square of one part at one height, centred on the spot.
     *
     * @param dy   the square's offset up
     * @param part the part it lays
     * @return each block of the square
     */
    private static List<Placement> square(int dy, Part part) {
        List<Placement> square = new ArrayList<>();
        for (int dx = -SQUARE_REACH; dx <= SQUARE_REACH; dx++) {
            for (int dz = -SQUARE_REACH; dz <= SQUARE_REACH; dz++) {
                square.add(at(dx, dy, dz, part));
            }
        }
        return square;
    }

    private static List<Placement> concat(List<Placement> first, List<Placement> second) {
        List<Placement> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
    }
}
