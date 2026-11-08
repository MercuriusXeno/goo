package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The tendril vignette a sight draws over the camera: the screen's rim fills
 * with a breathing mist, and tendrils curl in from the edges, each sight in
 * its own colors; Fungal Sight draws it in magenta, Sculker in sculk teal.
 * The tendrils are a live population, not a fixed set: they sprout at random
 * places along the edges at random times, never on top of one another, each
 * a thin, wispy vein with its own length, thickness and wiggling path that
 * often forks, heading mostly inward from its edge rather than along it. A tendril grows the way a root does, each stretch fading in
 * as its tip passes, lingers, then dies back from its tip, fading where it
 * lies, so the count on screen drifts and no layout repeats.
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
    /** The chance each tick a new tendril sprouts. */
    static final float SPROUT_CHANCE = 0.6f;
    /** How far apart along one edge two tendrils root at the least, as a share of the edge. */
    static final float ROOT_GAP = 0.04f;
    /** The most tendrils standing at once. */
    static final int MOST_TENDRILS = 48;
    /** The evenly spaced slots along each edge a tendril roots in, one tendril to a slot. */
    static final int SLOTS_PER_EDGE = 12;
    /** How far a tendril roots off its slot's middle at most, as a share of the edge. */
    static final float SLOT_JITTER = 0.25f / SLOTS_PER_EDGE;
    /** The rounded lobes of soft growth along each edge, evenly spaced. */
    static final int LOBES_PER_EDGE = 16;
    /** How far a lobe sits off its place at most, as a share of the edge. */
    static final float LOBE_JITTER = 0.2f / LOBES_PER_EDGE;
    /** The smallest and largest lobe, its reach in from the edge as a share of the screen's shorter side. */
    static final float SMALLEST_LOBE = 0.035f;
    static final float LARGEST_LOBE = 0.065f;
    /** How much a lobe breathes, as a share of its reach, and how fast. */
    private static final float LOBE_BREATH = 0.12f;
    private static final float SLOWEST_LOBE_BREATH = 0.03f;
    private static final float FASTEST_LOBE_BREATH = 0.07f;
    /** A lobe's soft halo, as a share past its own reach, and the halo's and the core's alpha. */
    private static final float HALO_SHARE = 1.4f;
    private static final float HALO_ALPHA = 0.16f;
    private static final float CORE_ALPHA = 0.42f;
    /** The rows a lobe draws in, as pixels. */
    private static final int LOBE_ROW = 2;
    /** The shortest and longest a tendril reaches, as a share of the screen's shorter side. */
    private static final float SHORTEST = 0.02f;
    private static final float LONGEST = 0.06f;
    /** The ticks a tendril takes to grow in and to wither back, at the least and at the most. */
    private static final int FASTEST_CHANGE_TICKS = 12;
    private static final int SLOWEST_CHANGE_TICKS = 40;
    /** The ticks a grown tendril lingers, at the least and at the most. */
    private static final int SHORTEST_LINGER_TICKS = 20;
    private static final int LONGEST_LINGER_TICKS = 160;
    /** The longest a tendril lives, growing, lingering and withering. */
    static final int LONGEST_LIFE_TICKS = SLOWEST_CHANGE_TICKS + LONGEST_LINGER_TICKS + SLOWEST_CHANGE_TICKS;
    /** How far a tendril's path may stray sideways from its root at any point, as a share of how far in it has come. */
    static final float MOST_SLANT = 0.35f;
    /** How far a tendril's path may turn each step, as a share of its length. */
    private static final float WANDER_STEP = 0.18f;
    /** The fewest and most waves a tendril's wiggle runs along its length. */
    private static final float FEWEST_WAVES = 1.5f;
    private static final float MOST_WAVES = 3.5f;
    /** The ticks a newly grown stretch takes to fade in, and a dying one to fade out. */
    private static final float STRETCH_FADE_TICKS = 8f;
    /** The tries a sprout takes to find a root clear of every other on its edge. */
    private static final int ROOT_TRIES = 8;
    /** How far a tendril sways at its tip at the most, as a share of the screen's shorter side. */
    private static final float MOST_SWAY = 0.012f;
    private static final float SLOWEST_SWAY_SPEED = 0.03f;
    private static final float FASTEST_SWAY_SPEED = 0.1f;
    /** The share of tendrils that root thick, and that fork. */
    private static final float THICK_SHARE = 0.35f;
    private static final float FORK_SHARE = 0.3f;
    /** How long a fork reaches, as a share of the tendril beyond where it leaves. */
    private static final float FORK_LENGTH = 0.6f;
    private static final int TENDRIL_STEPS = 36;
    private static final int FORK_STEPS = TENDRIL_STEPS / 2;
    /** Where along a tendril it may fork, from and to, in steps. */
    private static final int FORK_FROM = TENDRIL_STEPS / 4;
    private static final int FORK_TO = TENDRIL_STEPS * 3 / 4;
    private static final int TENDRIL_DOT = 2;
    private static final int THICK_DOT = 3;
    /** The share of a thick tendril, root outward, drawn thick. */
    private static final float THICK_REACH = 0.5f;
    private static final float TENDRIL_ALPHA = 0.5f;
    /** The most ticks one frame catches up on, so a pause does not sprout a crowd at once. */
    private static final int MOST_CATCH_UP_TICKS = 20;
    private static final int EDGES = 4;
    private static final int EDGE_TOP = 0;
    private static final int EDGE_BOTTOM = 1;
    private static final int EDGE_LEFT = 2;
    private static final int OPAQUE = 255;
    private static final float NO_TIME = Float.NaN;
    /** The share of its heading a path keeps, turned back, when it meets its slant. */
    private static final float HALF_TURN = 0.5f;
    private static final float HALF = 0.5f;
    /** A spread runs both ways off its middle, twice its half. */
    private static final float BOTH_WAYS = 2f;
    /** A tendril's fork step where it forks none. */
    private static final int NO_FORK = -1;
    /** The smoothstep's cubic terms, 3s² - 2s³, easing a tendril's growth in and out. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private final SplittableRandom random;
    private final List<Tendril> live = new ArrayList<>();
    private final List<Lobe> fringe;
    private float lastTime = NO_TIME;

    /**
     * @param seed the seed this vignette's tendrils sprout from and its fringe is laid from
     */
    public TendrilVignette(long seed) {
        this.random = new SplittableRandom(seed);
        this.fringe = layFringe(new SplittableRandom(~seed));
    }

    /**
     * One rounded lobe of the soft growth along an edge.
     *
     * @param edge          zero top, one bottom, two left, three right
     * @param along         where along the edge it sits, zero to one
     * @param radius        how far in it reaches, as a share of the screen's shorter side
     * @param breathSpeed   how fast it breathes, radians a tick
     * @param breathPhase   its breath's phase in radians
     */
    record Lobe(int edge, float along, float radius, float breathSpeed, float breathPhase) {
    }

    /**
     * Lays the fringe: the same number of rounded lobes along every edge,
     * evenly spaced, each a little off its place and its own size, so the
     * edges fill with soft growth that never reads as a pattern.
     *
     * @param laying the random the fringe is laid from
     * @return every lobe, edge by edge, in order along each
     */
    private static List<Lobe> layFringe(SplittableRandom laying) {
        List<Lobe> lobes = new ArrayList<>();
        for (int edge = 0; edge < EDGES; edge++) {
            for (int place = 0; place < LOBES_PER_EDGE; place++) {
                float middle = (place + HALF) / LOBES_PER_EDGE;
                lobes.add(new Lobe(edge, middle + spread(laying, LOBE_JITTER),
                        SMALLEST_LOBE + laying.nextFloat() * (LARGEST_LOBE - SMALLEST_LOBE),
                        SLOWEST_LOBE_BREATH + laying.nextFloat() * (FASTEST_LOBE_BREATH - SLOWEST_LOBE_BREATH),
                        laying.nextFloat() * Mth.TWO_PI));
            }
        }
        return List.copyOf(lobes);
    }

    private static float spread(SplittableRandom laying, float most) {
        return (laying.nextFloat() - HALF) * BOTH_WAYS * most;
    }

    /**
     * @return the fringe's lobes, edge by edge
     */
    List<Lobe> fringe() {
        return fringe;
    }

    /**
     * The slot a root along an edge falls in.
     *
     * @param along where along the edge, zero to one
     * @return the slot, zero to one short of the slots per edge
     */
    static int slotOf(float along) {
        return Mth.clamp((int) Math.floor(along * SLOTS_PER_EDGE), 0, SLOTS_PER_EDGE - 1);
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
     * One tendril standing: where it roots, its reach and its life.
     *
     * @param edge        zero top, one bottom, two left, three right
     * @param along       where along the edge it roots, zero to one
     * @param length      how far in it reaches when grown, as a share of the screen's shorter side
     * @param thick       whether it roots thick
     * @param bornAt      the game time it sprouted
     * @param growTicks   the ticks it takes to grow in
     * @param lingerTicks the ticks it lingers grown
     * @param witherTicks the ticks it takes to wither back
     * @param sway        how far it sways at its tip, as a share of the screen's shorter side
     * @param swaySpeed   how fast it sways, radians a tick
     * @param swayPhase   its sway's phase in radians
     * @param waves       the waves its wiggle runs along its length
     * @param path        its wandering path: each step's sideways offset, as a share of its length
     * @param forkAt      the step it forks at, or a negative number where it does not
     * @param forkPath    its fork's wandering path, as a share of the fork's length, empty where it does not fork
     */
    record Tendril(int edge, float along, float length, boolean thick, float bornAt, int growTicks,
                   int lingerTicks, int witherTicks, float sway, float swaySpeed, float swayPhase, float waves,
                   float[] path, int forkAt, float[] forkPath) {

        /**
         * @return true where the tendril forks
         */
        boolean forks() {
            return forkAt >= 0;
        }

        /**
         * @return the game time it is gone by
         */
        float goneAt() {
            return bornAt + growTicks + lingerTicks + witherTicks;
        }

        /**
         * How far its tip has grown at a time, easing out as it nears full length.
         *
         * @param time the game time
         * @return zero to one
         */
        float grownAt(float time) {
            return ease((time - bornAt) / growTicks);
        }

        /**
         * How strongly a stretch of the tendril shows at a time: fading in
         * as the tip passes it, full while it lingers, and fading out as the
         * tendril dies back from its tip toward its root.
         *
         * @param time  the game time
         * @param share the stretch's share along the tendril, zero at the root
         * @return zero to one
         */
        float stretchAt(float time, float share) {
            float grownAt = bornAt + growTicks * share;
            float diesAt = bornAt + growTicks + lingerTicks + witherTicks * (1f - share);
            float in = Mth.clamp((time - grownAt) / STRETCH_FADE_TICKS, 0f, 1f);
            float out = 1f - Mth.clamp((time - diesAt) / STRETCH_FADE_TICKS, 0f, 1f);
            return in * out;
        }

        private static float ease(float share) {
            float clamped = Mth.clamp(share, 0f, 1f);
            return clamped * clamped * (SMOOTH_SQUARE - SMOOTH_CUBE * clamped);
        }
    }

    /**
     * @return the tendrils standing now
     */
    List<Tendril> live() {
        return Collections.unmodifiableList(live);
    }

    /**
     * Runs the population up to a time: sprouts new tendrils at random on
     * each tick since the last, and lets the ones whose life has run out go.
     *
     * @param time the game time
     */
    void advance(float time) {
        if (Float.isNaN(lastTime) || time < lastTime) {
            lastTime = time;
        }
        int ticks = Math.min(MOST_CATCH_UP_TICKS, (int) Math.floor(time) - (int) Math.floor(lastTime));
        for (int tick = 1; tick <= ticks; tick++) {
            if (live.size() < MOST_TENDRILS && random.nextFloat() < SPROUT_CHANCE) {
                sprout((float) Math.floor(lastTime) + tick);
            }
        }
        live.removeIf(tendril -> time >= tendril.goneAt());
        lastTime = time;
    }

    /**
     * Sprouts a new tendril, every trait of it rolled at random, in an empty
     * slot along an edge, a little off the slot's middle; where a few tries
     * find every slot they pick taken, no tendril sprouts this time.
     *
     * @param bornAt the game time it sprouts
     */
    private void sprout(float bornAt) {
        for (int attempt = 0; attempt < ROOT_TRIES; attempt++) {
            int edge = random.nextInt(EDGES);
            int slot = random.nextInt(SLOTS_PER_EDGE);
            if (slotIsFree(edge, slot)) {
                float along = (slot + HALF) / SLOTS_PER_EDGE + spread(random, SLOT_JITTER);
                live.add(rolled(edge, along, bornAt));
                return;
            }
        }
    }

    private boolean slotIsFree(int edge, int slot) {
        return live.stream().noneMatch(other -> other.edge() == edge && slotOf(other.along()) == slot);
    }

    private Tendril rolled(int edge, float along, float bornAt) {
        int forkAt = random.nextFloat() < FORK_SHARE ? random.nextInt(FORK_FROM, FORK_TO + 1) : NO_FORK;
        return new Tendril(edge, along, between(SHORTEST, LONGEST),
                random.nextFloat() < THICK_SHARE, bornAt,
                random.nextInt(FASTEST_CHANGE_TICKS, SLOWEST_CHANGE_TICKS + 1),
                random.nextInt(SHORTEST_LINGER_TICKS, LONGEST_LINGER_TICKS + 1),
                random.nextInt(FASTEST_CHANGE_TICKS, SLOWEST_CHANGE_TICKS + 1),
                between(0f, MOST_SWAY), between(SLOWEST_SWAY_SPEED, FASTEST_SWAY_SPEED),
                between(0f, Mth.TWO_PI), between(FEWEST_WAVES, MOST_WAVES), wander(TENDRIL_STEPS), forkAt,
                forkAt < 0 ? new float[0] : wander(FORK_STEPS));
    }

    /**
     * A wandering path: each step turns a random amount off the last, so the
     * path drifts the way a root does rather than following a set curve.
     *
     * @param steps the steps along it
     * @return each step's sideways offset, as a share of the path's length
     */
    private float[] wander(int steps) {
        float[] path = new float[steps + 1];
        float heading = between(-WANDER_STEP, WANDER_STEP);
        for (int step = 1; step <= steps; step++) {
            heading += between(-WANDER_STEP, WANDER_STEP);
            float inward = (float) step / steps;
            float slant = MOST_SLANT * inward;
            path[step] = Mth.clamp(path[step - 1] + heading / steps * TENDRIL_STEPS * WANDER_STEP, -slant, slant);
            if (Math.abs(path[step]) >= slant) {
                heading = -heading * HALF_TURN;
            }
        }
        return path;
    }

    private float between(float low, float high) {
        return low + random.nextFloat() * (high - low);
    }

    /**
     * Runs the population to the frame's time, then draws the mist and the
     * tendrils at a strength.
     *
     * @param graphics the GUI graphics
     * @param time     the game time with the frame's partial tick
     * @param strength how fully the vignette stands, zero to one
     * @param look     the sight's colors
     */
    public void draw(GuiGraphicsExtractor graphics, float time, float strength, Look look) {
        advance(time);
        if (strength <= 0f) {
            return;
        }
        drawMist(graphics, time, strength, look.mistRgb());
        drawFringe(graphics, time, strength, look.mistRgb());
        Brush brush = new Brush(graphics, new Screen(graphics.guiWidth(), graphics.guiHeight()), time, strength,
                look.tendrilRgb());
        live.forEach(brush::draw);
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

    /**
     * Draws the fringe: each lobe a soft rounded mound of growth breathing
     * in from its edge, a faint halo under a denser core.
     *
     * @param graphics the GUI graphics
     * @param time     the game time with the frame's partial tick
     * @param strength how fully the vignette stands
     * @param rgb      the growth's color
     */
    private void drawFringe(GuiGraphicsExtractor graphics, float time, float strength, int rgb) {
        Screen screen = new Screen(graphics.guiWidth(), graphics.guiHeight());
        float shorter = Math.min(screen.width(), screen.height());
        int halo = ARGB.color(Math.round(HALO_ALPHA * strength * OPAQUE), rgb);
        int core = ARGB.color(Math.round(CORE_ALPHA * strength * OPAQUE), rgb);
        for (Lobe lobe : fringe) {
            float breath = 1f + LOBE_BREATH * Mth.sin(time * lobe.breathSpeed() + lobe.breathPhase());
            float reach = lobe.radius() * shorter * breath;
            drawLobe(graphics, screen, lobe, reach * HALO_SHARE, halo);
            drawLobe(graphics, screen, lobe, reach, core);
        }
    }

    /**
     * Draws one rounded mound in from an edge, row by row.
     *
     * @param graphics the GUI graphics
     * @param screen   the screen's size
     * @param lobe     the lobe
     * @param reach    how far in it reaches, in pixels
     * @param color    its color
     */
    private static void drawLobe(GuiGraphicsExtractor graphics, Screen screen, Lobe lobe, float reach, int color) {
        for (int inward = 0; inward < reach; inward += LOBE_ROW) {
            float half = (float) Math.sqrt(reach * reach - (float) inward * inward);
            int[] from = screen.from(lobe.edge(), lobe.along(), inward, -half);
            int[] to = screen.from(lobe.edge(), lobe.along(), inward + LOBE_ROW, half);
            graphics.fill(Math.min(from[0], to[0]), Math.min(from[1], to[1]), Math.max(from[0], to[0]),
                    Math.max(from[1], to[1]), color);
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

    /**
     * One frame's tendril drawing state: where it draws, the time, how
     * strongly and in what color.
     *
     * @param graphics   the GUI graphics
     * @param screen     the screen's size
     * @param time       the game time with the frame's partial tick
     * @param strength   how fully the vignette stands
     * @param tendrilRgb the tendrils' color
     */
    private record Brush(GuiGraphicsExtractor graphics, Screen screen, float time, float strength, int tendrilRgb) {

        /**
         * Draws a tendril as far as it has grown, and its fork where it has
         * grown past it.
         *
         * @param tendril the tendril
         */
        void draw(Tendril tendril) {
            float shorter = Math.min(screen.width(), screen.height());
            float length = tendril.length() * shorter;
            int reached = Math.round(TENDRIL_STEPS * tendril.grownAt(time));
            for (int step = 0; step <= reached; step++) {
                float share = (float) step / TENDRIL_STEPS;
                float side = tendril.path()[step] * length + swayAt(tendril, share, shorter);
                int size = tendril.thick() && share < THICK_REACH ? THICK_DOT : TENDRIL_DOT;
                dot(tendril, share * length, side, (1f - share) * tendril.stretchAt(time, share), size);
            }
            if (tendril.forks() && reached > tendril.forkAt()) {
                drawFork(tendril, length, shorter, reached);
            }
        }

        private void drawFork(Tendril tendril, float length, float shorter, int reached) {
            float rootShare = (float) tendril.forkAt() / TENDRIL_STEPS;
            float rootInward = rootShare * length;
            float rootSide = tendril.path()[tendril.forkAt()] * length + swayAt(tendril, rootShare, shorter);
            float forkLength = length * (1f - rootShare) * FORK_LENGTH;
            int forkReached = Math.min(FORK_STEPS, Math.round(FORK_STEPS
                    * (reached - tendril.forkAt()) / (float) (TENDRIL_STEPS - tendril.forkAt())));
            for (int step = 1; step <= forkReached; step++) {
                float share = (float) step / FORK_STEPS;
                float side = rootSide + tendril.forkPath()[step] * forkLength;
                float along = rootShare + (1f - rootShare) * share;
                dot(tendril, rootInward + share * forkLength, side,
                        (1f - rootShare) * (1f - share) * tendril.stretchAt(time, along), TENDRIL_DOT);
            }
        }

        private float swayAt(Tendril tendril, float share, float shorter) {
            return tendril.sway() * shorter * share * Mth.sin(time * tendril.swaySpeed() + tendril.swayPhase()
                    + share * tendril.waves() * Mth.TWO_PI);
        }

        private void dot(Tendril tendril, float inward, float side, float fade, int size) {
            int alpha = Math.round(Mth.clamp(TENDRIL_ALPHA * fade * strength, 0f, 1f) * OPAQUE);
            int[] at = screen.from(tendril.edge(), tendril.along(), inward, side);
            graphics.fill(at[0], at[1], at[0] + size, at[1] + size, ARGB.color(alpha, tendrilRgb));
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
