package com.mercuriusxeno.goo.client.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Sculker's growth along the screen's rim: a live population of thin veins,
 * each forking again and again into a web the way sculk veins spread over a
 * block face. Veins root in clumps, a few patches of rim thick with growth
 * and the rest bare; each clump lives a while and gives way to another
 * somewhere else, and each vein grows in from its root, lingers, then dies
 * back from its tips, so the web shifts and no layout repeats.
 * decision sculker-tendrils-are-a-branching-vein-network
 * decision sculker-veins-root-in-clusters-and-bare-gaps
 */
final class VeinNetwork {

    /** Every vein draws one pixel wide. */
    static final int THIN_STROKE = 1;
    /** The clumps rooting veins at once. */
    private static final int CLUMPS = 5;
    /** How far apart along one edge two clumps stand at the least, as a share of the edge. */
    private static final float CLUMP_GAP = 0.2f;
    /** How far a clump's roots spread off its middle at the most, as a share of the edge. */
    private static final float NARROWEST_CLUMP = 0.012f;
    private static final float WIDEST_CLUMP = 0.035f;
    /** The ticks a clump roots veins for, at the least and at the most. */
    private static final int SHORTEST_CLUMP_TICKS = 300;
    private static final int LONGEST_CLUMP_TICKS = 900;
    /** The chance each tick a new vein sprouts. */
    private static final float SPROUT_CHANCE = 0.5f;
    /** The most veins standing at once. */
    private static final int MOST_VEINS = 36;
    /** How near two roots on one edge may stand, as a share of the edge. */
    private static final float ROOT_GAP = 0.004f;
    /** The tries a sprout takes to find a root clear of every other. */
    private static final int ROOT_TRIES = 6;
    /** The shortest and longest trunk, as a share of the screen's shorter side. */
    private static final float SHORTEST_TRUNK = 0.05f;
    private static final float LONGEST_TRUNK = 0.11f;
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
    /** The ticks a vein takes to grow in and to wither back, at the least and at the most. */
    private static final int FASTEST_CHANGE_TICKS = 20;
    private static final int SLOWEST_CHANGE_TICKS = 50;
    /** The ticks a grown vein lingers, at the least and at the most. */
    private static final int SHORTEST_LINGER_TICKS = 60;
    private static final int LONGEST_LINGER_TICKS = 220;
    /** The ticks a newly grown stretch takes to fade in, and a dying one to fade out. */
    private static final float STRETCH_FADE_TICKS = 8f;
    /** A vein's alpha at its root, and the share of it left at its farthest tip. */
    private static final float VEIN_ALPHA = 0.55f;
    private static final float TIP_SHARE = 0.3f;
    /** The most ticks one frame catches up on. */
    private static final int MOST_CATCH_UP_TICKS = 20;
    private static final int EDGES = 4;
    private static final int OPAQUE = 255;
    private static final float NO_TIME = Float.NaN;
    private static final float HALF = 0.5f;

    private final SplittableRandom random;
    private final List<Clump> clumps = new ArrayList<>();
    private final List<Vein> live = new ArrayList<>();
    private float lastTime = NO_TIME;

    /**
     * @param seed the seed the veins and their clumps are rolled from
     */
    VeinNetwork(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /**
     * A patch of rim veins root in while it lives.
     *
     * @param edge   zero top, one bottom, two left, three right
     * @param middle where along the edge it centers, zero to one
     * @param spread how far its roots fall off its middle at the most, as a share of the edge
     * @param goneAt the game time it stops rooting veins
     */
    private record Clump(int edge, float middle, float spread, float goneAt) {
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
     * One vein standing: where it roots, its branches and its life.
     *
     * @param edge        zero top, one bottom, two left, three right
     * @param along       where along the edge it roots, zero to one
     * @param branches    its trunk first, then every fork
     * @param reach       how far its farthest tip stands along it, as a share of the screen's shorter side
     * @param bornAt      the game time it sprouted
     * @param growTicks   the ticks it takes to grow in
     * @param lingerTicks the ticks it lingers grown
     * @param witherTicks the ticks it takes to wither back
     */
    record Vein(int edge, float along, List<Branch> branches, float reach, float bornAt, int growTicks,
                int lingerTicks, int witherTicks) {

        /**
         * @return the deepest any of its forks stands, zero where it never forks
         */
        int deepestFork() {
            return branches.stream().mapToInt(Branch::depth).max().orElse(0);
        }

        /**
         * @return the game time it is gone by
         */
        float goneAt() {
            return bornAt + growTicks + lingerTicks + witherTicks;
        }

        /**
         * How strongly a point along the vein shows at a time: fading in as
         * growth passes it, full while it lingers, and fading out as the
         * vein dies back from its tips toward its root.
         *
         * @param time  the game time
         * @param share the point's share of the vein's reach, zero at the root
         * @return zero to one
         */
        float stretchAt(float time, float share) {
            float grownAt = bornAt + growTicks * share;
            float diesAt = bornAt + growTicks + lingerTicks + witherTicks * (1f - share);
            float in = Mth.clamp((time - grownAt) / STRETCH_FADE_TICKS, 0f, 1f);
            float out = 1f - Mth.clamp((time - diesAt) / STRETCH_FADE_TICKS, 0f, 1f);
            return in * out;
        }
    }

    /**
     * @return the veins standing now
     */
    List<Vein> live() {
        return Collections.unmodifiableList(live);
    }

    /**
     * Runs the population up to a time: lets spent clumps give way to new
     * ones, sprouts new veins into the clumps on each tick since the last,
     * and lets the veins whose life has run out go.
     *
     * @param time the game time
     */
    void advance(float time) {
        if (Float.isNaN(lastTime) || time < lastTime) {
            lastTime = time;
        }
        int ticks = Math.min(MOST_CATCH_UP_TICKS, (int) Math.floor(time) - (int) Math.floor(lastTime));
        for (int tick = 1; tick <= ticks; tick++) {
            tick((float) Math.floor(lastTime) + tick);
        }
        live.removeIf(vein -> time >= vein.goneAt());
        lastTime = time;
    }

    /**
     * One tick of the population: spent clumps give way to new ones, and a
     * vein may sprout.
     *
     * @param now the tick's game time
     */
    private void tick(float now) {
        clumps.removeIf(clump -> now >= clump.goneAt());
        while (clumps.size() < CLUMPS) {
            clumps.add(rolledClump(now));
        }
        if (live.size() < MOST_VEINS && random.nextFloat() < SPROUT_CHANCE) {
            sprout(now);
        }
    }

    /**
     * Rolls a clump on an edge and at a place along it clear of every other
     * clump on that edge, where a few tries find one.
     *
     * @param now the game time
     * @return the clump
     */
    private Clump rolledClump(float now) {
        int edge = random.nextInt(EDGES);
        float middle = between(WIDEST_CLUMP, 1f - WIDEST_CLUMP);
        for (int attempt = 0; attempt < ROOT_TRIES && crowdsAClump(edge, middle); attempt++) {
            edge = random.nextInt(EDGES);
            middle = between(WIDEST_CLUMP, 1f - WIDEST_CLUMP);
        }
        return new Clump(edge, middle, between(NARROWEST_CLUMP, WIDEST_CLUMP),
                now + random.nextInt(SHORTEST_CLUMP_TICKS, LONGEST_CLUMP_TICKS + 1));
    }

    private boolean crowdsAClump(int edge, float middle) {
        return clumps.stream().anyMatch(clump -> clump.edge() == edge && Math.abs(clump.middle() - middle) < CLUMP_GAP);
    }

    /**
     * Sprouts a new vein in a clump picked at random, rooted off the clump's
     * middle and clear of every other root; where a few tries find no clear
     * root, no vein sprouts this time.
     *
     * @param bornAt the game time it sprouts
     */
    private void sprout(float bornAt) {
        for (int attempt = 0; attempt < ROOT_TRIES; attempt++) {
            Clump clump = clumps.get(random.nextInt(clumps.size()));
            float along = Mth.clamp(clump.middle() + between(-clump.spread(), clump.spread()), 0f, 1f);
            if (live.stream().noneMatch(other -> other.edge() == clump.edge()
                    && Math.abs(other.along() - along) < ROOT_GAP)) {
                live.add(grown(clump.edge(), along, bornAt));
                return;
            }
        }
    }

    private Vein grown(int edge, float along, float bornAt) {
        List<Branch> branches = new ArrayList<>();
        int trunkSteps = Math.round(between(SHORTEST_TRUNK, LONGEST_TRUNK) / STEP);
        grow(branches, 0, 0f, 0f, between(-MOST_TRUNK_LEAN, MOST_TRUNK_LEAN), trunkSteps, 0f);
        float reach = (float) branches.stream()
                .mapToDouble(branch -> branch.reachAt(branch.inward().length - 1)).max().orElse(STEP);
        return new Vein(edge, along, List.copyOf(branches), reach, bornAt,
                random.nextInt(FASTEST_CHANGE_TICKS, SLOWEST_CHANGE_TICKS + 1),
                random.nextInt(SHORTEST_LINGER_TICKS, LONGEST_LINGER_TICKS + 1),
                random.nextInt(FASTEST_CHANGE_TICKS, SLOWEST_CHANGE_TICKS + 1));
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
        for (Vein vein : live) {
            for (Branch branch : vein.branches()) {
                drawBranch(graphics, screen, vein, branch, shorter, time, strength, rgb);
            }
        }
    }

    private static void drawBranch(GuiGraphicsExtractor graphics, TendrilVignette.Screen screen, Vein vein,
                                   Branch branch, float shorter, float time, float strength, int rgb) {
        for (int point = 1; point < branch.inward().length; point++) {
            float share = branch.reachAt(point) / vein.reach();
            float fade = vein.stretchAt(time, share) * (1f - (1f - TIP_SHARE) * share);
            int alpha = Math.round(Mth.clamp(VEIN_ALPHA * fade * strength, 0f, 1f) * OPAQUE);
            if (alpha > 0) {
                int[] from = screen.from(vein.edge(), vein.along(), branch.inward()[point - 1] * shorter,
                        branch.side()[point - 1] * shorter);
                int[] to = screen.from(vein.edge(), vein.along(), branch.inward()[point] * shorter,
                        branch.side()[point] * shorter);
                line(graphics, from, to, branch.stroke(), ARGB.color(alpha, rgb));
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
