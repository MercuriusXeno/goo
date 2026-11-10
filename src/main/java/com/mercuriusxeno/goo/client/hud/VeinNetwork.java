package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Sculker's growth along the screen's rim: lots of thin veins, each forking
 * again and again into a web the way sculk veins spread over a block face,
 * rooted in an even spread all around the rim, most of them joined to
 * their neighbours by thin links so the rim reads as one web. They grow in from their
 * roots as the vision starts, then stay where they grew while it lasts,
 * moving only by a small wriggle; the next vision grows a fresh web.
 * decision sculker-tendrils-are-a-branching-vein-network
 * decision sculker-veins-root-in-clusters-and-bare-gaps
 */
final class VeinNetwork {

    /** Every vein draws one pixel wide. */
    static final int THIN_STROKE = 1;
    /** The veins rooted along each edge, evenly spread. */
    private static final int VEINS_PER_EDGE = 28;
    /** The share of neighbouring veins a link joins. */
    private static final float LINK_SHARE = 0.8f;
    /** Where along a trunk a link may leave or join it, from and to, as a share of the trunk. */
    private static final float LINK_FROM = 0.25f;
    private static final float LINK_TO = 0.75f;
    /** How far a link bows off the straight line at the most, as a share of its length. */
    private static final float MOST_BOW = 0.25f;
    /** The ticks a link takes to grow across, and between both its ends growing and it setting out. */
    private static final int LINK_GROW_TICKS = 20;
    private static final int LINK_DELAY_TICKS = 4;
    /** The pixels between one point of a drawn link and the next. */
    private static final float LINK_PIXELS_A_POINT = 2f;
    /** The fewest points a drawn link runs through, its two ends. */
    private static final int FEWEST_LINK_POINTS = 2;
    /** The middle term of a curve through one bend, which both ends share. */
    private static final float BOTH_ENDS = 2f;
    /** How far a root falls off its even place at the most, as a share of the room each vein has. */
    private static final float ROOT_JITTER = 0.3f;
    /** The ticks after the vision starts a vein may wait before it grows, at the most. */
    private static final int LATEST_SPROUT_TICKS = 15;
    /** The ticks a vein takes to grow in, at the least and at the most. */
    private static final int FASTEST_GROW_TICKS = 20;
    private static final int SLOWEST_GROW_TICKS = 45;
    /** The shortest and longest trunk, as a share of the screen's shorter side. */
    private static final float SHORTEST_TRUNK = 0.04f;
    private static final float LONGEST_TRUNK = 0.10f;
    /** How long one step of a branch runs, as a share of the screen's shorter side. */
    private static final float STEP = 0.004f;
    /** How far a trunk sets off from straight inward at the most, in radians. */
    private static final float MOST_TRUNK_LEAN = 0.45f;
    /** How far a branch's heading turns each step at the most, in radians. */
    private static final float WANDER = 0.22f;
    /** How far from straight inward any branch may head, in radians, so a vein spreads along the rim but never back off it. */
    private static final float MOST_HEADING = 1.35f;
    /** How far a fork turns off its parent, at the least and at the most, in radians. */
    private static final float NARROWEST_FORK = 0.4f;
    private static final float WIDEST_FORK = 1.0f;
    /** The sign a fork's turn takes, to one side of its parent or the other. */
    private static final float TURN_LEFT = -1f;
    private static final float TURN_RIGHT = 1f;
    /** The chance each step a branch forks, once a fifth of its way out. */
    private static final float FORK_CHANCE = 0.14f;
    private static final float FORK_FROM = 0.2f;
    /** How long a fork runs, as a share of what its parent has left, at the least and at the most. */
    private static final float SHORTEST_FORK = 0.5f;
    private static final float LONGEST_FORK = 0.8f;
    /** The deepest a fork forks: a trunk is zero, its forks one, their forks two. */
    private static final int DEEPEST_FORK = 3;
    /** The shortest a branch runs, in steps; a fork shorter than this is not grown. */
    private static final int FEWEST_STEPS = 3;
    /** The ticks a newly grown stretch takes to fade in. */
    private static final float STRETCH_FADE_TICKS = 8f;
    /** How far a vein wriggles at its farthest tip at the most, as a share of the screen's shorter side. */
    private static final float MOST_WRIGGLE = 0.005f;
    private static final float SLOWEST_WRIGGLE_SPEED = 0.03f;
    private static final float FASTEST_WRIGGLE_SPEED = 0.08f;
    /** The fewest and most waves a vein's wriggle runs along its reach. */
    private static final float FEWEST_WAVES = 1f;
    private static final float MOST_WAVES = 2.5f;
    /** A vein's alpha at its root, and the share of it left at its farthest tip. */
    private static final float VEIN_ALPHA = 0.55f;
    private static final float TIP_SHARE = 0.3f;
    private static final int EDGES = 4;
    private static final int OPAQUE = 255;
    private static final float NO_TIME = Float.NaN;
    private static final float HALF = 0.5f;

    private final SplittableRandom random;
    private final List<Vein> grown = new ArrayList<>();
    private final List<Link> links = new ArrayList<>();
    private float lastTime = NO_TIME;

    /**
     * @param seed the seed every web the veins grow is rolled from
     */
    VeinNetwork(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /**
     * One run of a vein: the trunk out of its root, or a fork off another run.
     *
     * @param depth     zero for the trunk, one for its forks, two for theirs
     * @param inward    each point's distance in from the edge, as a share of the screen's shorter side
     * @param side      each point's distance along the edge off the root, as a share of the screen's shorter side
     * @param fromReach how far along the vein, root outward, its first point stands, as a share of the screen's shorter side
     */
    record Branch(int depth, float[] inward, float[] side, float fromReach) {

        /**
         * @return how wide it draws, in pixels
         */
        int stroke() {
            return THIN_STROKE;
        }

        /**
         * @param point a point's index
         * @return how far along the vein the point stands, root outward, as a share of the screen's shorter side
         */
        float reachAt(int point) {
            return fromReach + point * STEP;
        }
    }

    /**
     * One vein standing: where it roots, its branches, when it grows in and how it wriggles.
     *
     * @param edge         zero top, one bottom, two left, three right
     * @param along        where along the edge it roots, zero to one
     * @param branches     its trunk first, then every fork
     * @param reach        how far its farthest tip stands along it, as a share of the screen's shorter side
     * @param bornAt       the game time it starts growing
     * @param growTicks    the ticks it takes to grow in
     * @param wriggle      how far it wriggles at its farthest tip, as a share of the screen's shorter side
     * @param wriggleSpeed how fast it wriggles, radians a tick
     * @param wrigglePhase  its wriggle's phase in radians
     * @param waves        the waves its wriggle runs along its reach
     */
    record Vein(int edge, float along, List<Branch> branches, float reach, float bornAt, int growTicks,
                float wriggle, float wriggleSpeed, float wrigglePhase, float waves) {

        /**
         * @return the deepest any of its forks stands, zero where it never forks
         */
        int deepestFork() {
            return branches.stream().mapToInt(Branch::depth).max().orElse(0);
        }

        /**
         * How strongly a point along the vein shows at a time: fading in as
         * growth passes it, and full from then on.
         *
         * @param time  the game time
         * @param share the point's share of the vein's reach, zero at the root
         * @return zero to one
         */
        float stretchAt(float time, float share) {
            return Mth.clamp((time - bornAt - growTicks * share) / STRETCH_FADE_TICKS, 0f, 1f);
        }

        /**
         * How far a point along the vein has wriggled sideways at a time,
         * none at the root and most at the farthest tip.
         *
         * @param time  the game time
         * @param share the point's share of the vein's reach, zero at the root
         * @return its sideways offset, as a share of the screen's shorter side
         */
        float wriggleAt(float time, float share) {
            return wriggle * share * Mth.sin(time * wriggleSpeed + wrigglePhase + share * waves * Mth.TWO_PI);
        }
    }

    /**
     * A thin vein joining one vein's trunk to its neighbour's.
     *
     * @param from      the vein it leaves from
     * @param to        the neighbouring vein it joins
     * @param fromPoint the point along {@code from}'s trunk it leaves at
     * @param toPoint   the point along {@code to}'s trunk it joins at
     * @param bow       how far it bows off the straight line, as a share of its length, to one side or the other
     * @param startsAt  the game time it starts growing, once both its ends have grown
     */
    record Link(Vein from, Vein to, int fromPoint, int toPoint, float bow, float startsAt) {
    }

    /**
     * @return the veins standing now
     */
    List<Vein> live() {
        return Collections.unmodifiableList(grown);
    }

    /**
     * @return the links joining neighbouring veins now
     */
    List<Link> links() {
        return Collections.unmodifiableList(links);
    }

    /**
     * Grows a fresh web as the vision starts, the first time this is asked
     * after the vision was hidden, and from then on leaves it standing.
     *
     * @param time the game time
     */
    void advance(float time) {
        if (Float.isNaN(lastTime) || time < lastTime) {
            growWeb(time);
        }
        lastTime = time;
    }

    /** Lets the web go as the vision ends, so the next vision grows a fresh one. */
    void hide() {
        grown.clear();
        links.clear();
        lastTime = NO_TIME;
    }

    /**
     * Roots an even spread of veins along every edge, each a little off its
     * even place, and sets each growing in soon after the start.
     *
     * @param start the game time the vision starts
     */
    private void growWeb(float start) {
        grown.clear();
        links.clear();
        for (int edge = 0; edge < EDGES; edge++) {
            Vein last = null;
            for (int place = 0; place < VEINS_PER_EDGE; place++) {
                float along = (place + HALF + between(-ROOT_JITTER, ROOT_JITTER)) / VEINS_PER_EDGE;
                Vein vein = grown(edge, along, start + random.nextInt(LATEST_SPROUT_TICKS + 1));
                grown.add(vein);
                if (last != null && random.nextFloat() < LINK_SHARE) {
                    links.add(linked(last, vein));
                }
                last = vein;
            }
        }
    }

    /**
     * Joins two neighbouring veins: from a point along one trunk to a point
     * along the other's, bowed to one side, setting out once both points
     * have grown.
     *
     * @param from the vein it leaves from
     * @param to   the neighbouring vein it joins
     * @return the link
     */
    private Link linked(Vein from, Vein to) {
        int fromPoint = trunkPoint(from);
        int toPoint = trunkPoint(to);
        float startsAt = Math.max(grownAt(from, fromPoint), grownAt(to, toPoint)) + LINK_DELAY_TICKS;
        return new Link(from, to, fromPoint, toPoint, between(-MOST_BOW, MOST_BOW), startsAt);
    }

    private int trunkPoint(Vein vein) {
        int last = vein.branches().get(0).inward().length - 1;
        return Math.round(last * between(LINK_FROM, LINK_TO));
    }

    private static float grownAt(Vein vein, int trunkPoint) {
        return vein.bornAt() + vein.growTicks() * vein.branches().get(0).reachAt(trunkPoint) / vein.reach();
    }

    private Vein grown(int edge, float along, float bornAt) {
        List<Branch> branches = new ArrayList<>();
        int trunkSteps = Math.round(between(SHORTEST_TRUNK, LONGEST_TRUNK) / STEP);
        grow(branches, 0, 0f, 0f, between(-MOST_TRUNK_LEAN, MOST_TRUNK_LEAN), trunkSteps, 0f);
        float reach = (float) branches.stream()
                .mapToDouble(branch -> branch.reachAt(branch.inward().length - 1)).max().orElse(STEP);
        return new Vein(edge, along, List.copyOf(branches), reach, bornAt,
                random.nextInt(FASTEST_GROW_TICKS, SLOWEST_GROW_TICKS + 1), between(0f, MOST_WRIGGLE),
                between(SLOWEST_WRIGGLE_SPEED, FASTEST_WRIGGLE_SPEED), between(0f, Mth.TWO_PI),
                between(FEWEST_WAVES, MOST_WAVES));
    }

    /**
     * Grows one branch step by step, its heading wandering and kept off the
     * rim, and on the way forks branches of its own, each turned off it and
     * shorter, down to the deepest fork.
     *
     * @param branches  where every branch grown is gathered
     * @param depth     the branch's depth
     * @param inward    where it starts in from the edge
     * @param side      where it starts along the edge, off the root
     * @param heading   its heading, radians off straight inward
     * @param steps     how many steps it runs
     * @param fromReach how far along the vein it starts
     */
    private void grow(List<Branch> branches, int depth, float inward, float side, float heading, int steps,
                      float fromReach) {
        float[] inwards = new float[steps + 1];
        float[] sides = new float[steps + 1];
        inwards[0] = inward;
        sides[0] = side;
        float turning = heading;
        List<Fork> forks = new ArrayList<>();
        for (int step = 1; step <= steps; step++) {
            turning = Mth.clamp(turning + between(-WANDER, WANDER), -MOST_HEADING, MOST_HEADING);
            inwards[step] = Math.max(0f, inwards[step - 1] + Mth.cos(turning) * STEP);
            sides[step] = sides[step - 1] + Mth.sin(turning) * STEP;
            if (forksHere(depth, step, steps)) {
                forks.add(new Fork(step, turning));
            }
        }
        Branch branch = new Branch(depth, inwards, sides, fromReach);
        branches.add(branch);
        forks.forEach(fork -> growFork(branches, branch, fork, steps));
    }

    /**
     * Where a branch forks: a step past a fifth of its way, taken at random,
     * on a branch short of the deepest fork.
     *
     * @param depth the branch's depth
     * @param step  the step it stands at
     * @param steps the steps it runs
     * @return true where it forks at this step
     */
    private boolean forksHere(int depth, int step, int steps) {
        return depth < DEEPEST_FORK && step >= steps * FORK_FROM && random.nextFloat() < FORK_CHANCE;
    }

    /**
     * A point a branch forks at, and its heading there.
     *
     * @param step    the step it forks at
     * @param heading the parent's heading there, radians off straight inward
     */
    private record Fork(int step, float heading) {
    }

    /**
     * Grows a fork off its parent: turned to one side or the other, and
     * running a share of what the parent has left, where that share is long
     * enough to grow.
     *
     * @param branches where every branch grown is gathered
     * @param parent   the branch it forks off
     * @param fork     where it forks
     * @param steps    the steps the parent runs
     */
    private void growFork(List<Branch> branches, Branch parent, Fork fork, int steps) {
        int forkSteps = Math.round((steps - fork.step()) * between(SHORTEST_FORK, LONGEST_FORK));
        if (forkSteps < FEWEST_STEPS) {
            return;
        }
        float turn = between(NARROWEST_FORK, WIDEST_FORK) * (random.nextBoolean() ? TURN_LEFT : TURN_RIGHT);
        grow(branches, parent.depth() + 1, parent.inward()[fork.step()], parent.side()[fork.step()],
                Mth.clamp(fork.heading() + turn, -MOST_HEADING, MOST_HEADING), forkSteps,
                parent.reachAt(fork.step()));
    }

    private float between(float low, float high) {
        return low + random.nextFloat() * (high - low);
    }

    /**
     * Draws every vein as far as it has grown, each a one-pixel line.
     *
     * @param graphics the GUI graphics
     * @param screen   the screen's size
     * @param time     the game time with the frame's partial tick
     * @param strength how fully the vignette stands
     * @param rgb      the veins' color
     */
    void draw(GuiGraphicsExtractor graphics, TendrilVignette.Screen screen, float time, float strength, int rgb) {
        float shorter = Math.min(screen.width(), screen.height());
        for (Vein vein : grown) {
            for (Branch branch : vein.branches()) {
                drawBranch(graphics, new Stroke(screen, vein, shorter, time), branch, strength, rgb);
            }
        }
        for (Link link : links) {
            drawLink(graphics, link, new Stroke(screen, link.from(), shorter, time),
                    new Stroke(screen, link.to(), shorter, time), strength * linkFade(link), rgb);
        }
    }

    /**
     * How faint a link draws: as faint as the two trunks are where it meets them.
     *
     * @param link the link
     * @return zero to one
     */
    private static float linkFade(Link link) {
        float share = (link.from().branches().get(0).reachAt(link.fromPoint()) / link.from().reach()
                + link.to().branches().get(0).reachAt(link.toPoint()) / link.to().reach()) * HALF;
        return 1f - (1f - TIP_SHARE) * share;
    }

    /**
     * Draws a link as far across as it has grown: a curve from its point on
     * one trunk, bowed off the straight line, to its point on the other.
     *
     * @param graphics the GUI graphics
     * @param link     the link
     * @param from     where the vein it leaves draws
     * @param to       where the vein it joins draws
     * @param strength how strongly it draws, the vignette's strength times its fade
     * @param rgb      the veins' color
     */
    private static void drawLink(GuiGraphicsExtractor graphics, Link link, Stroke from, Stroke to, float strength,
                                 int rgb) {
        float across = Mth.clamp((from.time() - link.startsAt()) / LINK_GROW_TICKS, 0f, 1f);
        int alpha = Math.round(Mth.clamp(VEIN_ALPHA * strength, 0f, 1f) * OPAQUE);
        if (across <= 0f || alpha <= 0) {
            return;
        }
        int[] start = from.pointAt(link.from().branches().get(0), link.fromPoint());
        int[] end = to.pointAt(link.to().branches().get(0), link.toPoint());
        float[] bend = bendOf(start, end, link.bow());
        float length = Mth.length(end[0] - start[0], end[1] - start[1]);
        int points = Math.max(FEWEST_LINK_POINTS, Math.round(length / LINK_PIXELS_A_POINT));
        int reached = Math.round(points * across);
        int[] last = start;
        for (int point = 1; point <= reached; point++) {
            int[] next = onCurve(start, bend, end, (float) point / points);
            line(graphics, last, next, THIN_STROKE, ARGB.color(alpha, rgb));
            last = next;
        }
    }

    /**
     * The control point a link's curve bows toward: off the middle of the
     * straight line, square to it, by the bow's share of its length.
     *
     * @param start where the link leaves, x then y
     * @param end   where it joins, x then y
     * @param bow   how far it bows, as a share of its length
     * @return the control point, x then y
     */
    private static float[] bendOf(int[] start, int[] end, float bow) {
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        return new float[] {(start[0] + end[0]) * HALF - dy * bow, (start[1] + end[1]) * HALF + dx * bow};
    }

    /**
     * A point along a curve from one end to the other through its bend.
     *
     * @param start where the curve leaves, x then y
     * @param bend  the control point it bows toward, x then y
     * @param end   where it ends, x then y
     * @param share how far along it, zero to one
     * @return the point, x then y
     */
    private static int[] onCurve(int[] start, float[] bend, int[] end, float share) {
        float rest = 1f - share;
        float x = rest * rest * start[0] + BOTH_ENDS * rest * share * bend[0] + share * share * end[0];
        float y = rest * rest * start[1] + BOTH_ENDS * rest * share * bend[1] + share * share * end[1];
        return new int[] {Math.round(x), Math.round(y)};
    }

    /**
     * Where one vein draws this frame: the screen, the vein, the screen's
     * shorter side and the time.
     *
     * @param screen  the screen's size
     * @param vein    the vein
     * @param shorter the screen's shorter side, in pixels
     * @param time    the game time with the frame's partial tick
     */
    private record Stroke(TendrilVignette.Screen screen, Vein vein, float shorter, float time) {

        /**
         * @param branch the branch
         * @param point  a point's index along it
         * @return where the point lands on the screen this frame, x then y
         */
        int[] pointAt(Branch branch, int point) {
            float share = branch.reachAt(point) / vein.reach();
            float side = branch.side()[point] + vein.wriggleAt(time, share);
            return screen.from(vein.edge(), vein.along(), branch.inward()[point] * shorter, side * shorter);
        }
    }

    private static void drawBranch(GuiGraphicsExtractor graphics, Stroke stroke, Branch branch, float strength,
                                   int rgb) {
        Vein vein = stroke.vein();
        for (int point = 1; point < branch.inward().length; point++) {
            float share = branch.reachAt(point) / vein.reach();
            float fade = vein.stretchAt(stroke.time(), share) * (1f - (1f - TIP_SHARE) * share);
            int alpha = Math.round(Mth.clamp(VEIN_ALPHA * fade * strength, 0f, 1f) * OPAQUE);
            if (alpha > 0) {
                line(graphics, stroke.pointAt(branch, point - 1), stroke.pointAt(branch, point), branch.stroke(),
                        ARGB.color(alpha, rgb));
            }
        }
    }

    /**
     * Draws a line a pixel at a time, so a slanted run stays one pixel wide
     * rather than filling the box between its ends.
     *
     * @param graphics the GUI graphics
     * @param from     where it starts, x then y
     * @param to       where it ends, x then y
     * @param stroke   how wide it draws, in pixels
     * @param color    its color
     */
    private static void line(GuiGraphicsExtractor graphics, int[] from, int[] to, int stroke, int color) {
        int steps = Math.max(Math.abs(to[0] - from[0]), Math.abs(to[1] - from[1]));
        for (int step = 1; step <= steps; step++) {
            int x = Math.round(from[0] + (to[0] - from[0]) * (float) step / steps - HALF * (stroke - 1));
            int y = Math.round(from[1] + (to[1] - from[1]) * (float) step / steps - HALF * (stroke - 1));
            graphics.fill(x, y, x + stroke, y + stroke, color);
        }
    }
}
