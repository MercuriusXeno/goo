package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * One caster's laser scorch: a mark dropped each time the struck spot moves
 * a spacing along a face, the gap a fast rake leaves bridged with marks
 * whose ages run between its ends, so the trail cools from the impact
 * backward; the mark under a beam held still is burnt afresh. Client-only
 * bookkeeping: it reads and writes no blocks.
 * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
 */
final class ScorchTrail {

    /** Ticks a mark takes to cool from white-hot and fade away. */
    static final double FADE_TICKS = 80;
    /** The furthest apart two neighboring marks of one rake sit, in blocks. */
    static final double MARK_SPACING = 0.12;
    /** Past this jump along one face the ray left the face between frames, so nothing bridges it. */
    private static final double LONGEST_BRIDGE = 3;
    /**
     * Past this many ticks since the newest mark burnt, the beam stopped and
     * started again rather than raked, so nothing bridges the two spots.
     */
    static final double LONGEST_PAUSE = 2;
    /** Two points this close along a face's axis sit on one plane. */
    private static final double SAME_PLANE = 1e-3;
    /** How far into its face a mark reaches to find the block it burns on, in blocks. */
    private static final double INTO_THE_FACE = 1e-3;

    private final Deque<Mark> marks = new ArrayDeque<>();

    /**
     * One burnt spot on a block face.
     *
     * @param point  where the ray struck
     * @param face   the face it struck
     * @param bornAt the game time, in ticks, the ray last burnt it
     */
    record Mark(Vec3 point, Direction face, double bornAt) {

        /**
         * How hot the mark still burns.
         *
         * @param now the game time, in ticks
         * @return 1 when just burnt, falling to 0 at the fade time
         */
        double heat(double now) {
            return Math.clamp(1 - (now - bornAt) / FADE_TICKS, 0, 1);
        }

        /**
         * The block the mark burns on: the one just behind its face.
         *
         * @return the block's position
         */
        BlockPos block() {
            return BlockPos.containing(point.subtract(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(INTO_THE_FACE)));
        }
    }

    /**
     * Burns the spot the ray strikes this frame into the trail.
     *
     * @param point where the ray struck
     * @param face  the face it struck
     * @param now   the game time, in ticks
     */
    void burn(Vec3 point, Direction face, double now) {
        Mark newest = marks.peekLast();
        double gap = newest == null ? Double.MAX_VALUE : newest.point().distanceTo(point);
        if (!onOnePlane(newest, point, face) || gap > LONGEST_BRIDGE || now - newest.bornAt() > LONGEST_PAUSE) {
            marks.addLast(new Mark(point, face, now));
        } else if (gap < MARK_SPACING) {
            marks.pollLast();
            marks.addLast(new Mark(newest.point(), face, now));
        } else {
            bridge(newest, point, now);
        }
    }

    /**
     * Fills the gap from the newest mark to the struck spot with marks no
     * further apart than the spacing, their ages running between the two.
     *
     * @param from  the newest mark
     * @param point where the ray struck
     * @param now   the game time, in ticks
     */
    private void bridge(Mark from, Vec3 point, double now) {
        int steps = (int) Math.ceil(from.point().distanceTo(point) / MARK_SPACING);
        for (int step = 1; step <= steps; step++) {
            double along = (double) step / steps;
            marks.addLast(new Mark(from.point().lerp(point, along), from.face(),
                    from.bornAt() + (now - from.bornAt()) * along));
        }
    }

    private static boolean onOnePlane(@Nullable Mark newest, Vec3 point, Direction face) {
        if (newest == null || newest.face() != face) {
            return false;
        }
        Direction.Axis axis = face.getAxis();
        return Math.abs(newest.point().get(axis) - point.get(axis)) < SAME_PLANE;
    }

    /**
     * Drops every mark that has faded.
     *
     * @param now the game time, in ticks
     */
    void expire(double now) {
        marks.removeIf(mark -> now - mark.bornAt() >= FADE_TICKS);
    }

    /**
     * The marks still showing, oldest first.
     *
     * @return the marks
     */
    List<Mark> marks() {
        return List.copyOf(marks);
    }

    /**
     * The marks still showing whose block still stands, oldest first.
     *
     * @param blockStands whether a block stands at a position
     * @return the marks to draw
     */
    List<Mark> marksOnBlocks(Predicate<BlockPos> blockStands) {
        return marks.stream().filter(mark -> blockStands.test(mark.block())).toList();
    }

    /**
     * Whether every mark has faded.
     *
     * @return true for a trail with no mark left
     */
    boolean isEmpty() {
        return marks.isEmpty();
    }
}
