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
 * The fixed layout of one Unmake drink's tree, laid the frame each block
 * first appears and never laid again, so a stream keeps its route however
 * the hand moves: each block's far side, the stream it joins and how far
 * along that stream it joins. The first block runs to the glove; each block
 * after it joins the standing stream whose line runs nearest it, a little
 * toward the hand from the nearest point so it arrives at a shallow angle.
 * Only the ends of the paths follow the hand.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkLayout {

    /** Blocks toward the hand a join is pushed past the point of the trunk nearest the joining block. */
    static final double LEAD = 1.2;
    private static final double HALF = 0.5;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    /**
     * One block's place in the tree.
     *
     * @param pos     the block
     * @param farSide where its path starts, half its span behind its middle away from where its path runs
     * @param trunk   the block whose stream it joins, or null for the stream entering the glove
     * @param joinAt  blocks along the trunk's path it joins at, 0 for the stream entering the glove
     */
    public record Node(BlockPos pos, Vec3 farSide, @Nullable BlockPos trunk, double joinAt) {
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

    /**
     * @return every node, the one entering the glove first and each after the stream it joins
     */
    public Collection<Node> nodes() {
        return nodes.values();
    }

    /**
     * @param pos a block
     * @return its node, or null while it has not been laid
     */
    public @Nullable Node node(BlockPos pos) {
        return nodes.get(pos);
    }

    /**
     * Lays every block not yet laid, nearest the glove first, each joining
     * the standing stream whose line runs nearest it; a block already laid
     * is left as it is.
     *
     * @param blocks the drink's blocks
     * @param glove  the glove this frame
     */
    public void place(Collection<BlockPos> blocks, Vec3 glove) {
        List<BlockPos> fresh = new ArrayList<>();
        for (BlockPos pos : blocks) {
            if (!nodes.containsKey(pos)) {
                fresh.add(pos.immutable());
            }
        }
        fresh.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(glove)));
        for (BlockPos pos : fresh) {
            Vec3 center = Vec3.atCenterOf(pos);
            Node trunk = nearestTrunk(center, glove);
            if (trunk == null) {
                nodes.put(pos, new Node(pos, farSideOf(center, glove), null, 0));
            } else {
                Line line = lineOf(trunk, glove);
                double at = Math.min(line.length(), line.nearestDistance(center) + LEAD);
                nodes.put(pos, new Node(pos, farSideOf(center, line.pointAt(at)), trunk.pos(), at));
            }
        }
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
        Node trunk = node.trunk() == null ? null : nodes.get(node.trunk());
        if (trunk == null) {
            return new Line(node.farSide(), glove);
        }
        Line trunkLine = lineOf(trunk, glove);
        return new Line(node.farSide(), trunkLine.pointAt(Math.min(node.joinAt(), trunkLine.length())));
    }

    private @Nullable Node nearestTrunk(Vec3 center, Vec3 glove) {
        Node nearest = null;
        double least = Double.MAX_VALUE;
        for (Node node : nodes.values()) {
            Line line = lineOf(node, glove);
            double distance = line.pointAt(line.nearestDistance(center)).distanceToSqr(center);
            if (distance < least) {
                least = distance;
                nearest = node;
            }
        }
        return nearest;
    }
}
