package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

/**
 * The tendril vignette a sight draws over the camera: the screen's rim fills
 * with a breathing mist, and tendrils curl in from the edges, undulating,
 * each sight in its own colors. Fungal Sight draws it in magenta, Sculker in
 * sculk teal. Every tendril rolls its own shape from its edge and place: its
 * root wanders from an even spacing, and its length, sway, speed, waves,
 * curl and thickness are its own; it grows and shrinks back slowly on its
 * own clock, and some fork a side branch, so no two read alike.
 * sight-lengthens-shift-and-outlines-fungus
 * sculker-sight-persists-where-echoes-have-touched
 */
public final class TendrilVignette {

    /** Bands the mist is drawn in, rim to inside. */
    private static final int MIST_BANDS = 24;
    /** How far in the mist reaches, as a share of the screen's shorter side. */
    private static final float MIST_DEPTH = 0.16f;
    /** The mist's alpha at the very rim. */
    private static final float MIST_RIM_ALPHA = 0.42f;
    /** How much the mist's alpha breathes, as a share of itself. */
    private static final float MIST_BREATH = 0.15f;
    private static final float MIST_BREATH_SPEED = 0.05f;
    /** Tendrils along each edge. */
    private static final int TENDRILS_PER_EDGE = 5;
    /** How far in a tendril of middling length reaches, as a share of the screen's shorter side. */
    private static final float TENDRIL_LENGTH = 0.07f;
    /** How far a tendril of middling sway sways, as a share of the screen's shorter side. */
    private static final float TENDRIL_SWAY = 0.012f;
    private static final float TENDRIL_SWAY_SPEED = 0.12f;
    /** Waves along a tendril of middling waviness. */
    private static final float TENDRIL_WAVES = 1.5f;
    /** How fast a tendril of middling pace grows and shrinks back, radians a tick. */
    private static final float GROW_SPEED = 0.02f;
    /** The share of its length a tendril keeps when it has shrunk back the most. */
    private static final float SHRUNK_SHARE = 0.55f;
    /** How far a tendril's root wanders from an even spacing, as a share of that spacing. */
    private static final float ROOT_WANDER = 0.7f;
    /** The spread each rolled trait takes about its middle value, as a share of it. */
    private static final float LENGTH_SPREAD = 0.9f;
    private static final float SWAY_SPREAD = 1.2f;
    private static final float SPEED_SPREAD = 1f;
    private static final float WAVES_SPREAD = 1.2f;
    private static final float GROW_SPREAD = 1.4f;
    /** The farthest a tendril bends to one side toward its tip, as a share of its length, and the span either way. */
    private static final float MOST_CURL = 0.35f;
    private static final float CURL_SPAN = MOST_CURL + MOST_CURL;
    /** A tendril's branch point when it forks none. */
    static final float NO_BRANCH = -1f;
    /** The share of tendrils that root thick, and that fork a branch. */
    private static final float THICK_SHARE = 0.35f;
    private static final float BRANCH_SHARE = 0.4f;
    /** Where along a tendril its branch may leave, and how much of the rest of its length the branch reaches. */
    private static final float BRANCH_FROM = 0.3f;
    private static final float BRANCH_SPAN = 0.4f;
    private static final float BRANCH_LENGTH = 0.55f;
    /** How sharply a branch turns off its tendril, as a share of the branch's length. */
    private static final float BRANCH_TURN = 0.6f;
    private static final int TENDRIL_STEPS = 18;
    private static final int BRANCH_STEPS = TENDRIL_STEPS / 3;
    private static final int TENDRIL_DOT = 2;
    private static final int THICK_DOT = 3;
    /** The share of a thick tendril, root outward, drawn thick. */
    private static final float THICK_REACH = 0.4f;
    private static final float TENDRIL_ALPHA = 0.55f;
    private static final int EDGES = 4;
    private static final float HALF = 0.5f;
    private static final int EDGE_TOP = 0;
    private static final int EDGE_BOTTOM = 1;
    private static final int EDGE_LEFT = 2;
    private static final int OPAQUE = 255;
    /** The odd constants of the SplitMix64 step that rolls each trait. */
    private static final long GOLDEN_GAMMA = 0x9E37_79B9_7F4A_7C15L;
    private static final long MIX_ONE = 0xBF58_476D_1CE4_E5B9L;
    private static final long MIX_TWO = 0x94D0_49BB_1331_11EBL;
    private static final int SHIFT_ONE = 30;
    private static final int SHIFT_TWO = 27;
    private static final int SHIFT_THREE = 31;
    private static final int UNIT_BITS = 24;
    private static final float UNIT = 1f / (1 << UNIT_BITS);
    private static final int EDGE_SEED = 31;

    private TendrilVignette() {
    }

    /**
     * A sight's vignette colors.
     *
     * @param mistRgb    the mist's color, 0xRRGGBB
     * @param tendrilRgb the tendrils' color, 0xRRGGBB
     */
    public record Look(int mistRgb, int tendrilRgb) {
    }

    /**
     * One tendril's own shape, rolled from its edge and place on it.
     *
     * @param along      where along the edge it roots, zero to one
     * @param length     how far in it reaches, as a share of the screen's shorter side
     * @param sway       how far it sways, as a share of the screen's shorter side
     * @param swaySpeed  how fast it sways, radians a tick
     * @param waves      waves along its length
     * @param curl       how far it bends to one side toward its tip, as a share of its length
     * @param phase      its sway's phase in radians
     * @param growSpeed  how fast it grows and shrinks back, radians a tick
     * @param thick      whether it roots thick
     * @param branchAt   where along it a side branch leaves, zero to one, or a negative number for none
     */
    record Shape(float along, float length, float sway, float swaySpeed, float waves, float curl, float phase,
                 float growSpeed, boolean thick, float branchAt) {
    }

    /**
     * Rolls a tendril's shape from its edge and its place along it, the same
     * every frame, different from every other tendril's.
     *
     * @param edge  zero top, one bottom, two left, three right
     * @param index its place along the edge
     * @return its shape
     */
    static Shape shapeOf(int edge, int index) {
        Rolls rolls = new Rolls(edge * EDGE_SEED + index);
        float along = (index + HALF + (rolls.next() - HALF) * ROOT_WANDER) / TENDRILS_PER_EDGE;
        float length = TENDRIL_LENGTH * spread(rolls.next(), LENGTH_SPREAD);
        float sway = TENDRIL_SWAY * spread(rolls.next(), SWAY_SPREAD);
        float swaySpeed = TENDRIL_SWAY_SPEED * spread(rolls.next(), SPEED_SPREAD);
        float waves = TENDRIL_WAVES * spread(rolls.next(), WAVES_SPREAD);
        float curl = (rolls.next() - HALF) * CURL_SPAN;
        float phase = rolls.next() * Mth.TWO_PI;
        float growSpeed = GROW_SPEED * spread(rolls.next(), GROW_SPREAD);
        boolean thick = rolls.next() < THICK_SHARE;
        float branchAt = rolls.next() < BRANCH_SHARE ? BRANCH_FROM + rolls.next() * BRANCH_SPAN : NO_BRANCH;
        return new Shape(along, length, sway, swaySpeed, waves, curl, phase, growSpeed, thick, branchAt);
    }

    /**
     * A trait's value from a roll: its middle value's share, spread evenly about one.
     *
     * @param roll   a roll, zero to one
     * @param spread the spread about one
     * @return the share of the middle value
     */
    private static float spread(float roll, float spread) {
        return 1f + (roll - HALF) * spread;
    }

    /**
     * Draws the mist and the tendrils at a strength.
     *
     * @param graphics the GUI graphics
     * @param time     the game time with the frame's partial tick
     * @param strength how fully the vignette stands, zero to one
     * @param look     the sight's colors
     */
    public static void draw(GuiGraphicsExtractor graphics, float time, float strength, Look look) {
        if (strength <= 0f) {
            return;
        }
        drawMist(graphics, time, strength, look.mistRgb());
        drawTendrils(graphics, time, strength, look.tendrilRgb());
    }

    private static void drawMist(GuiGraphicsExtractor graphics, float time, float strength, int mistRgb) {
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float depth = Math.min(width, height) * MIST_DEPTH;
        float breath = 1f + MIST_BREATH * Mth.sin(time * MIST_BREATH_SPEED);
        for (int band = 0; band < MIST_BANDS; band++) {
            int outer = Math.round(depth * band / MIST_BANDS);
            int inner = Math.round(depth * (band + 1) / MIST_BANDS);
            float toward = 1f - (float) band / MIST_BANDS;
            float alpha = MIST_RIM_ALPHA * toward * toward * breath * strength;
            int color = ARGB.color(Math.round(Mth.clamp(alpha, 0f, 1f) * OPAQUE), mistRgb);
            drawRing(graphics, outer, inner, width, height, color);
        }
    }

    private static void drawRing(GuiGraphicsExtractor graphics, int outer, int inner, int width, int height,
                                 int color) {
        if (inner <= outer) {
            return;
        }
        graphics.fill(outer, outer, width - outer, inner, color);
        graphics.fill(outer, height - inner, width - outer, height - outer, color);
        graphics.fill(outer, inner, inner, height - inner, color);
        graphics.fill(width - inner, inner, width - outer, height - inner, color);
    }

    private static void drawTendrils(GuiGraphicsExtractor graphics, float time, float strength, int tendrilRgb) {
        Screen screen = new Screen(graphics.guiWidth(), graphics.guiHeight());
        float shorter = Math.min(screen.width(), screen.height());
        Brush brush = new Brush(graphics, screen, shorter, time, strength, tendrilRgb);
        for (int edge = 0; edge < EDGES; edge++) {
            for (int index = 0; index < TENDRILS_PER_EDGE; index++) {
                drawTendril(brush, edge, shapeOf(edge, index));
            }
        }
    }

    /**
     * Draws a tendril grown to its length this frame, and its branch where it forks one.
     *
     * @param brush the frame's drawing state
     * @param edge  the edge it grows from
     * @param shape its shape
     */
    private static void drawTendril(Brush brush, int edge, Shape shape) {
        float length = grownLength(brush, shape);
        float sway = shape.sway() * brush.shorter();
        Fork fork = null;
        for (int step = 0; step <= TENDRIL_STEPS; step++) {
            float share = (float) step / TENDRIL_STEPS;
            float inward = share * length;
            float side = sideAt(brush.time(), shape, share, sway) + shape.curl() * length * share * share;
            brush.dot(edge, shape.along(), inward, side, 1f - share, dotSize(shape, share));
            if (fork == null && forksAt(shape, share)) {
                fork = new Fork(inward, side, share);
            }
        }
        if (fork != null) {
            drawBranch(brush, edge, shape, fork, length * (1f - fork.share()) * BRANCH_LENGTH);
        }
    }

    /**
     * How far in a tendril reaches this frame, in screen pixels, grown and shrunk back on its own clock.
     *
     * @param brush the frame's drawing state
     * @param shape its shape
     * @return its length this frame
     */
    private static float grownLength(Brush brush, Shape shape) {
        float grown = SHRUNK_SHARE + (1f - SHRUNK_SHARE) * HALF
                * (1f + Mth.sin(brush.time() * shape.growSpeed() + shape.phase()));
        return shape.length() * grown * brush.shorter();
    }

    private static int dotSize(Shape shape, float share) {
        return shape.thick() && share < THICK_REACH ? THICK_DOT : TENDRIL_DOT;
    }

    private static boolean forksAt(Shape shape, float share) {
        return shape.branchAt() >= 0 && share >= shape.branchAt();
    }

    /**
     * Where a branch leaves its tendril.
     *
     * @param inward how far in from the edge
     * @param side   how far to the side of its tendril's line
     * @param share  the share along its tendril
     */
    private record Fork(float inward, float side, float share) {
    }

    /**
     * Draws a tendril's side branch, turning off it the way it does not curl
     * and fading on from where it leaves.
     *
     * @param brush  the frame's drawing state
     * @param edge   the edge its tendril grows from
     * @param shape  its tendril's shape
     * @param fork   where it leaves its tendril
     * @param length how far it reaches
     */
    private static void drawBranch(Brush brush, int edge, Shape shape, Fork fork, float length) {
        float turn = shape.curl() >= 0 ? -BRANCH_TURN : BRANCH_TURN;
        for (int step = 1; step <= BRANCH_STEPS; step++) {
            float share = (float) step / BRANCH_STEPS;
            float inward = fork.inward() + share * length;
            float side = fork.side() + turn * length * share;
            brush.dot(edge, shape.along(), inward, side, (1f - fork.share()) * (1f - share), TENDRIL_DOT);
        }
    }

    /**
     * How far a tendril sways off its line at a share along it: nothing at
     * its root, its full sway at its tip.
     *
     * @param time  the game time
     * @param shape its shape
     * @param share the share along it, zero at the root
     * @param sway  its sway, in screen pixels
     * @return the sideways offset, in screen pixels
     */
    private static float sideAt(float time, Shape shape, float share, float sway) {
        return sway * share * Mth.sin(time * shape.swaySpeed() + shape.phase() + share * shape.waves() * Mth.TWO_PI);
    }

    /**
     * One frame's tendril drawing state: where it draws, how large the
     * screen's shorter side is, the time, how strongly and in what color.
     *
     * @param graphics   the GUI graphics
     * @param screen     the screen's size
     * @param shorter    the screen's shorter side
     * @param time       the game time with the frame's partial tick
     * @param strength   how fully the vignette stands
     * @param tendrilRgb the tendrils' color
     */
    private record Brush(GuiGraphicsExtractor graphics, Screen screen, float shorter, float time, float strength,
                         int tendrilRgb) {

        /**
         * Draws one dot of a tendril.
         *
         * @param edge   the edge its tendril grows from
         * @param along  where along the edge its tendril roots
         * @param inward how far in from the edge the dot stands
         * @param side   how far to the side of its tendril's line it stands
         * @param fade   how strongly it shows, zero to one
         * @param size   its size in pixels
         */
        void dot(int edge, float along, float inward, float side, float fade, int size) {
            int alpha = Math.round(Mth.clamp(TENDRIL_ALPHA * fade * strength, 0f, 1f) * OPAQUE);
            int[] at = screen.from(edge, along, inward, side);
            graphics.fill(at[0], at[1], at[0] + size, at[1] + size, ARGB.color(alpha, tendrilRgb));
        }
    }

    /**
     * A tendril's rolls, each the next step of SplitMix64 from its seed, so
     * every tendril rolls the same traits every frame.
     */
    private static final class Rolls {

        private long state;

        /**
         * @param seed the tendril's seed
         */
        Rolls(long seed) {
            this.state = seed;
        }

        /**
         * @return the next roll, zero to one
         */
        float next() {
            state += GOLDEN_GAMMA;
            long mixed = (state ^ (state >>> SHIFT_ONE)) * MIX_ONE;
            mixed = (mixed ^ (mixed >>> SHIFT_TWO)) * MIX_TWO;
            mixed ^= mixed >>> SHIFT_THREE;
            return (mixed >>> (Long.SIZE - UNIT_BITS)) * UNIT;
        }
    }

    /**
     * The screen's size, and the point a step inward from an edge lands at.
     *
     * @param width  the screen's width
     * @param height the screen's height
     */
    private record Screen(int width, int height) {

        int[] from(int edge, float along, float inward, float side) {
            return switch (edge) {
                case EDGE_TOP -> new int[] {Math.round(along * width + side), Math.round(inward)};
                case EDGE_BOTTOM -> new int[] {Math.round(along * width + side), Math.round(height - inward)};
                case EDGE_LEFT -> new int[] {Math.round(inward), Math.round(along * height + side)};
                default -> new int[] {Math.round(width - inward), Math.round(along * height + side)};
            };
        }
    }
}
