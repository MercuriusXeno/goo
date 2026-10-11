package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The layout of one Unmake drink's tree, every stream pathing to the hand:
 * a block joins the nearest standing stream that runs nearer the hand and
 * still carries liquid where it would join, its own or a tributary's, a little
 * toward the hand from the nearest point so it arrives at a shallow angle, or
 * runs to the glove when none does; a stream whose trunk carries nothing at
 * its join any more re-roots the same way, its block end anchored and its hand
 * end gliding onto the new course rather than snapping, so the tree thins
 * toward the hand as blocks finish instead of channelling through what has
 * gone, while a trunk whose own liquid has passed keeps carrying what joins
 * it, so one stream enters the palm. The trunk arrives down
 * a line lifted over the look, in over the fingertips into the palm, and the
 * line follows the look with a lag, so the hand pulls the stream.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkLayout {

    /** Blocks toward the hand a join is pushed past the point of the trunk nearest the joining block. */
    static final double LEAD = 1.2;
    /** Ticks the pull takes to close most of the way to a new look, so the stream sways rather than snaps. */
    static final double PULL_LAG = 6;
    /** The rise over the look the trunk arrives down, as a slope: about thirty degrees, over the fingertips into the palm. */
    static final double DESCENT = 0.6;
    /** Ticks a re-rooted stream's hand end takes to glide most of the way onto its new course. */
    static final double GLIDE = 6;
    /** The share of a glide past which the end is on its course and the glide is over. */
    private static final double SETTLED = 0.999;
    private static final double HALF = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * Whether a laid stream's liquid is still flowing at a distance along its path.
     */
    public interface Flowing {

        /**
         * @param pos      the stream's block
         * @param distance blocks along the stream's own path from its far side
         * @return whether its liquid is still flowing there
         */
        boolean at(BlockPos pos, double distance);
    }

    /** Every stream flowing everywhere, as at a drink's start. */
    static final Flowing EVERY = (pos, distance) -> true;

    /**
     * One block's place in the tree: its far side, the stream it joins and
     * how far along it, and where its hand end is this frame.
     */
    public static final class Node {
        private final BlockPos pos;
        private final Vec3 farSide;
        private @Nullable BlockPos trunk;
        private double joinAt;
        private Vec3 end;
        private @Nullable Vec3 glideFrom;
        private double glideAt;

        private Node(BlockPos pos, Vec3 farSide, @Nullable BlockPos trunk, double joinAt, Vec3 end) {
            this.pos = pos;
            this.farSide = farSide;
            this.trunk = trunk;
            this.joinAt = joinAt;
            this.end = end;
        }

        /**
         * @return the block
         */
        public BlockPos pos() {
            return pos;
        }

        /**
         * @return where its path starts, half its span behind its middle away from where its path first ran
         */
        public Vec3 farSide() {
            return farSide;
        }

        /**
         * @return the block whose stream it joins, or null for a stream entering the glove
         */
        public @Nullable BlockPos trunk() {
            return trunk;
        }

        /**
         * @return blocks along the trunk's path it joins at, 0 for a stream entering the glove
         */
        public double joinAt() {
            return joinAt;
        }

        /**
         * @return whether its hand end is still gliding onto a new course
         */
        public boolean gliding() {
            return glideFrom != null;
        }

        /**
         * Where the path's hand end is this frame: on its target, or gliding
         * onto it from where it was when the stream re-rooted.
         *
         * @param target where the path should end: its join on its trunk, or the glove
         * @param now    the game time, with the partial tick
         * @return the hand end
         */
        public Vec3 endToward(Vec3 target, double now) {
            if (glideFrom == null) {
                end = target;
                return end;
            }
            double share = 1 - Math.exp(-Math.max(0, now - glideAt) / GLIDE);
            end = glideFrom.lerp(target, share);
            if (share >= SETTLED) {
                glideFrom = null;
            }
            return end;
        }

        private void reroot(@Nullable BlockPos newTrunk, double newJoinAt, double now) {
            glideFrom = end;
            glideAt = now;
            trunk = newTrunk;
            joinAt = newJoinAt;
        }
    }

    /**
     * The straight line a stream runs, from its far side to its end.
     *
     * @param from the far side
     * @param to   the end
     */
    record Line(Vec3 from, Vec3 to) {

        double length() {
            return to.distanceTo(from);
        }

        /**
         * @param point a point
         * @return the blocks along the line of the point nearest it, 0 to the length
         */
        double nearestDistance(Vec3 point) {
            double length = length();
            return length == 0 ? 0 : Math.clamp(point.subtract(from).dot(to.subtract(from)) / length, 0, length);
        }

        /**
         * @param distance blocks along the line
         * @return the point there
         */
        Vec3 pointAt(double distance) {
            double length = length();
            return length == 0 ? from : from.add(to.subtract(from).scale(distance / length));
        }
    }

    private final Map<BlockPos, Node> nodes = new LinkedHashMap<>();
    private @Nullable Vec3 pull;
    private double pulledAt;

    /**
     * @return every node, each after the stream it joins
     */
    public Collection<Node> nodes() {
        List<Node> ordered = new ArrayList<>();
        List<Node> waiting = new ArrayList<>(nodes.values());
        while (!waiting.isEmpty()) {
            List<Node> ready = new ArrayList<>();
            for (Node node : waiting) {
                if (trunkListed(node, ordered)) {
                    ready.add(node);
                }
            }
            if (ready.isEmpty()) {
                ready.addAll(waiting);
            }
            ordered.addAll(ready);
            waiting.removeAll(ready);
        }
        return ordered;
    }

    /**
     * @param node    a node
     * @param ordered the nodes listed so far
     * @return whether the node's trunk is listed, or it has none standing
     */
    private boolean trunkListed(Node node, List<Node> ordered) {
        return node.trunk == null || !nodes.containsKey(node.trunk) || ordered.contains(nodes.get(node.trunk));
    }

    /**
     * The line the drink's trunk arrives down: the look lifted by
     * {@link #DESCENT}, so the stream comes in from ahead and above, over the
     * fingertips into the palm, rather than down the forearm's axis.
     *
     * @param look the drinker's unit look
     * @return the unit direction from the hand toward where the trunk arrives from
     */
    public static Vec3 arrivalOf(Vec3 look) {
        return look.add(0, DESCENT, 0).normalize();
    }

    /**
     * The direction the drink's trunk arrives into the hand along: down the
     * {@link #arrivalOf arrival line}, followed with a short lag so a swing
     * of the look drags the stream round rather than snapping it.
     *
     * @param look the drinker's unit look this frame
     * @param now  the game time, with the partial tick
     * @return the unit direction the trunk flows as it enters the hand
     */
    public Vec3 pullToward(Vec3 look, double now) {
        Vec3 target = arrivalOf(look).reverse();
        if (pull == null) {
            pull = target;
        } else {
            double share = 1 - Math.exp(-Math.max(0, now - pulledAt) / PULL_LAG);
            Vec3 pulled = pull.lerp(target, share);
            pull = pulled.lengthSqr() > 0 ? pulled.normalize() : target;
        }
        pulledAt = now;
        return pull;
    }

    /**
     * @param pos a block
     * @return its node, or null while it has not been laid
     */
    public @Nullable Node node(BlockPos pos) {
        return nodes.get(pos);
    }

    /**
     * Lays every block with every stream flowing, as at a drink's start.
     *
     * @param blocks the drink's blocks
     * @param glove  the glove this frame
     */
    public void place(Collection<BlockPos> blocks, Vec3 glove) {
        place(blocks, glove, 0, EVERY);
    }

    /**
     * Lays every block not yet laid, nearest the glove first, each joining
     * the nearest flowing stream nearer the hand or running to the glove, and
     * re-roots every laid block whose trunk has run dry at its join.
     *
     * @param blocks  the drink's blocks
     * @param glove   the glove this frame
     * @param now     the game time, with the partial tick
     * @param flowing whether a laid stream's liquid still flows at a distance along its path
     */
    public void place(Collection<BlockPos> blocks, Vec3 glove, double now, Flowing flowing) {
        List<BlockPos> order = new ArrayList<>();
        blocks.forEach(pos -> order.add(pos.immutable()));
        order.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(glove)));
        for (BlockPos pos : order) {
            Node node = nodes.get(pos);
            if (node == null) {
                lay(pos, glove, flowing);
            } else if (node.trunk != null && !trunkFlows(node, flowing)) {
                reroot(node, glove, now, flowing);
            }
        }
    }

    private boolean trunkFlows(Node node, Flowing flowing) {
        return nodes.containsKey(node.trunk) && flowing.at(node.trunk, node.joinAt);
    }

    private void lay(BlockPos pos, Vec3 glove, Flowing flowing) {
        Vec3 center = Vec3.atCenterOf(pos);
        Node trunk = nearestTrunk(center, glove, flowing, null);
        if (trunk == null) {
            nodes.put(pos, new Node(pos, farSideOf(center, glove), null, 0, glove));
            return;
        }
        Line line = lineOf(trunk, glove);
        double at = joinOn(line, center);
        nodes.put(pos, new Node(pos, farSideOf(center, line.pointAt(at)), trunk.pos, at, line.pointAt(at)));
    }

    private void reroot(Node node, Vec3 glove, double now, Flowing flowing) {
        Vec3 center = Vec3.atCenterOf(node.pos);
        Node trunk = nearestTrunk(center, glove, flowing, node);
        if (trunk == null) {
            node.reroot(null, 0, now);
        } else {
            node.reroot(trunk.pos, joinOn(lineOf(trunk, glove), center), now);
        }
    }

    private static double joinOn(Line line, Vec3 center) {
        return Math.min(line.length(), line.nearestDistance(center) + LEAD);
    }

    /**
     * @param center the block's middle
     * @param to     where its path runs
     * @return its far side, half its span behind its middle away from where the path runs
     */
    public static Vec3 farSideOf(Vec3 center, Vec3 to) {
        Vec3 along = to.subtract(center);
        along = along.lengthSqr() > 0 ? along.normalize() : UP;
        return center.subtract(along.scale(DrinkStream.BLOCK_SPAN * HALF));
    }

    /**
     * @param node  a node
     * @param glove the glove
     * @return the straight line its stream runs, from its far side to its join or the glove
     */
    Line lineOf(Node node, Vec3 glove) {
        Node trunk = node.trunk == null ? null : nodes.get(node.trunk);
        if (trunk == null) {
            return new Line(node.farSide, glove);
        }
        Line trunkLine = lineOf(trunk, glove);
        return new Line(node.farSide, trunkLine.pointAt(Math.min(node.joinAt, trunkLine.length())));
    }

    /**
     * The standing stream a block joins: the one whose line runs nearest it
     * among those whose blocks are nearer the glove, which do not themselves
     * run through the block's own stream, and whose liquid still flows where
     * the block would join.
     *
     * @param center  the block's middle
     * @param glove   the glove
     * @param flowing whether a stream still flows at a distance along its path
     * @param self    the block's own node, where it is re-rooting, else null
     * @return the trunk, or null for a stream running to the glove
     */
    private @Nullable Node nearestTrunk(Vec3 center, Vec3 glove, Flowing flowing, @Nullable Node self) {
        Node nearest = null;
        double least = Double.MAX_VALUE;
        for (Node node : nodes.values()) {
            if (!candidate(node, center, glove, self)) {
                continue;
            }
            Line line = lineOf(node, glove);
            double distance = line.pointAt(line.nearestDistance(center)).distanceToSqr(center);
            if (distance < least && flowing.at(node.pos, joinOn(line, center))) {
                least = distance;
                nearest = node;
            }
        }
        return nearest;
    }

    /**
     * @param node   a standing node
     * @param center a block's middle
     * @param glove  the glove
     * @param self   the block's own node, or null
     * @return whether the block may join the node's stream: another's, nearer the glove, and not running down its own
     */
    private boolean candidate(Node node, Vec3 center, Vec3 glove, @Nullable Node self) {
        return node != self && Vec3.atCenterOf(node.pos).distanceToSqr(glove) < center.distanceToSqr(glove)
                && !runsThrough(node, self);
    }

    /**
     * @param node     a node
     * @param ancestor a node, or null
     * @return whether the node's stream runs down the ancestor's, so the ancestor cannot join it
     */
    private boolean runsThrough(Node node, @Nullable Node ancestor) {
        for (Node up = node; up != null && ancestor != null; up = up.trunk == null ? null : nodes.get(up.trunk)) {
            if (up == ancestor) {
                return true;
            }
        }
        return false;
    }
}
