package com.mercuriusxeno.goo.throwing;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The cone a stream delivery sprays: its apex at the glove hand, its axis
 * the player's look, reaching the delivery's range and opening to the
 * delivery's cone angle, apex angle edge to edge (decision stream-delivery-held-cone).
 */
public final class StreamCone {

    private static final double HALF = 0.5;
    /** An up vector to cross the axis with, swapped for east when the axis runs straight up or down. */
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final double NEARLY_VERTICAL = 0.99;
    private static final double FULL_TURN = 2 * Math.PI;
    /** Blocks from the apex a point stands at the apex, too near for its angle off the axis to mean anything. */
    private static final double AT_THE_APEX = 1e-6;
    /** A turn leaning this little one way never reaches a band's edge that way. */
    private static final double NONE_THAT_WAY = 1e-9;

    private StreamCone() {
    }

    /**
     * Whether a point stands inside the cone: ahead of the apex, within the
     * range, and within half the cone angle of the axis.
     *
     * @param apex        the cone's apex
     * @param axis        the cone's axis, any length
     * @param range       the cone's reach in blocks
     * @param coneDegrees the cone's apex angle in degrees
     * @param point       the point tested
     * @return true for a point inside the cone
     */
    public static boolean contains(Vec3 apex, Vec3 axis, double range, double coneDegrees, Vec3 point) {
        Vec3 offset = point.subtract(apex);
        double distance = offset.length();
        if (distance > range) {
            return false;
        }
        double along = offset.dot(axis.normalize());
        return along > 0 && along >= distance * Math.cos(Math.toRadians(coneDegrees * HALF));
    }

    /**
     * Whether a point stands inside a cone of a shape: the apex itself, or a point within the reach whose angle off
     * the axis stays inside the shape's widths at its turn about the axis, as {@link #launchDirection} spreads them.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param apex  the cone's apex
     * @param axis  the cone's axis, any length
     * @param reach the cone's reach in blocks
     * @param shape the cone's widths across and up and down
     * @param point the point tested
     * @return true for a point inside the cone
     */
    public static boolean contains(Vec3 apex, Vec3 axis, double reach, ConeShape shape, Vec3 point) {
        Vec3 offset = point.subtract(apex);
        double distance = offset.length();
        if (distance < AT_THE_APEX) {
            return true;
        }
        return distance <= reach && sectionOf(axis, offset).fits(shape);
    }

    /**
     * Whether a thing's box stands inside a cone of a shape: its middle inside the shape and the reach, the one test
     * every coned stream's scan of the living and the loose reads.
     * decision cone-style-abilities-are-reviewed-for-consistency
     *
     * @param apex  the cone's apex
     * @param axis  the cone's axis, any length
     * @param reach the cone's reach in blocks
     * @param shape the cone's widths across and up and down
     * @param box   the thing's box
     * @return true where its middle stands inside
     */
    public static boolean containsTheMiddleOf(Vec3 apex, Vec3 axis, double reach, ConeShape shape, AABB box) {
        return contains(apex, axis, reach, shape, box.getCenter());
    }

    /**
     * A point pulled inside a cone of a shape: its angle off the axis held to the shape's width at its turn, and its
     * distance from the apex to the reach; a point inside stands where it is, and a point pulled passes
     * {@link #contains}.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param apex  the cone's apex
     * @param axis  the cone's axis, any length
     * @param reach the cone's reach in blocks
     * @param shape the cone's widths across and up and down
     * @param point the point pulled inside
     * @return the point, inside the cone
     */
    public static Vec3 pulledInside(Vec3 apex, Vec3 axis, double reach, ConeShape shape, Vec3 point) {
        if (contains(apex, axis, reach, shape, point)) {
            return point;
        }
        Vec3 offset = point.subtract(apex);
        Section section = sectionOf(axis, offset);
        // a hair short of the reach, as the rim, so rounding leaves the pulled point inside
        return apex.add(section.heldInside(shape).scale(Math.min(offset.length(), reach * Section.RIM_SLACK)));
    }

    private static Section sectionOf(Vec3 axis, Vec3 offset) {
        Vec3 forward = axis.normalize();
        Vec3 side = side(forward);
        Vec3 lift = side.cross(forward);
        return new Section(forward, side, lift, offset.dot(forward), offset.dot(side), offset.dot(lift));
    }

    /**
     * A point's place about a cone's axis.
     *
     * @param forward the cone's unit axis
     * @param side    the unit direction across
     * @param lift    the unit direction up and down
     * @param along   blocks along the axis
     * @param across  blocks across
     * @param upDown  blocks up and down
     */
    private record Section(Vec3 forward, Vec3 side, Vec3 lift, double along, double across, double upDown) {

        /** Slack under the rim a pulled point is held to, so a point on the rim reads inside. */
        private static final double RIM_SLACK = 1 - 1e-9;

        boolean fits(ConeShape shape) {
            if (shape.band()) {
                return along > 0 && Math.abs(Math.atan2(across, along)) <= halfOf(shape.sideDegrees())
                        && Math.abs(Math.atan2(upDown, along)) <= halfOf(shape.liftDegrees());
            }
            return offAxis() <= halfOf(shape.degreesAt(turn()));
        }

        Vec3 heldInside(ConeShape shape) {
            if (shape.band()) {
                double sideways = Math.clamp(Math.atan2(across, Math.abs(along)), -halfOf(shape.sideDegrees()),
                        halfOf(shape.sideDegrees())) * RIM_SLACK;
                double lifted = Math.clamp(Math.atan2(upDown, Math.abs(along)), -halfOf(shape.liftDegrees()),
                        halfOf(shape.liftDegrees())) * RIM_SLACK;
                return forward.add(side.scale(Math.tan(sideways))).add(lift.scale(Math.tan(lifted))).normalize();
            }
            double turn = turn();
            double tilt = Math.min(offAxis(), halfOf(shape.degreesAt(turn))) * RIM_SLACK;
            Vec3 off = side.scale(Math.cos(turn)).add(lift.scale(Math.sin(turn)));
            return forward.scale(Math.cos(tilt)).add(off.scale(Math.sin(tilt))).normalize();
        }

        private double offAxis() {
            return Math.atan2(Math.hypot(across, upDown), along);
        }

        private double turn() {
            return Math.atan2(upDown, across);
        }

        private static double halfOf(double degrees) {
            return Math.toRadians(degrees * HALF);
        }
    }

    /**
     * A unit vector square to the axis, to build a cone's cross-section on.
     *
     * @param axis the axis, any length
     * @return a unit vector square to it
     */
    public static Vec3 side(Vec3 axis) {
        Vec3 forward = axis.normalize();
        return forward.cross(Math.abs(forward.y) > NEARLY_VERTICAL ? EAST : UP).normalize();
    }

    /**
     * A direction leaving the apex inside the cone: tilted off the axis by a
     * share of the cone's half angle and turned about the axis by a share of
     * a full turn, so even shares fill the cone's mouth.
     * mycosis-spore-stream-buds-and-poisons
     *
     * @param axis        the cone's axis, any length
     * @param coneDegrees the cone's apex angle in degrees
     * @param tiltShare   how far off the axis, zero on it and one at the rim
     * @param turnShare   how far about the axis, zero to one for a full turn
     * @return the unit direction
     */
    public static Vec3 launchDirection(Vec3 axis, double coneDegrees, double tiltShare, double turnShare) {
        return launchDirection(axis, ConeShape.round(coneDegrees), tiltShare, turnShare);
    }

    /**
     * A direction leaving the apex inside a cone of a shape: tilted off the axis by a share of the cone's half
     * angle at its turn, so a flattened cone reaches far across and little up and down.
     * decision spray-item-takes-different-nozzles-doing-different-things
     * decision fan-nozzle-flattens-and-widens-the-cone
     *
     * @param axis      the cone's axis, any length
     * @param shape     the cone's widths across and up and down
     * @param tiltShare how far off the axis, zero on it and one at the rim
     * @param turnShare how far about the axis, zero to one for a full turn
     * @return the unit direction
     */
    public static Vec3 launchDirection(Vec3 axis, ConeShape shape, double tiltShare, double turnShare) {
        Vec3 forward = axis.normalize();
        Vec3 side = side(forward);
        Vec3 lift = side.cross(forward);
        if (shape.band()) {
            return bandDirection(forward, side, lift, shape, tiltShare, turnShare);
        }
        double turn = FULL_TURN * turnShare;
        double tilt = Math.toRadians(shape.degreesAt(turn) * HALF) * tiltShare;
        Vec3 off = side.scale(Math.cos(turn)).add(lift.scale(Math.sin(turn)));
        return forward.scale(Math.cos(tilt)).add(off.scale(Math.sin(tilt))).normalize();
    }

    /**
     * The direction on a cone's rim at a turn about its axis: for a cone the shape's half width at that turn, for a
     * band the edge of its rectangle that way, so a section drawn through the rim directions outlines the shape
     * {@link #contains} tests.
     * decision spray-abilities-render-procedurally-bound-to-their-cone
     *
     * @param axis      the cone's axis, any length
     * @param shape     the cone's widths across and up and down
     * @param turnShare how far about the axis, zero to one for a full turn, zero across to the side
     * @return the unit direction
     */
    public static Vec3 rimDirection(Vec3 axis, ConeShape shape, double turnShare) {
        if (!shape.band()) {
            return launchDirection(axis, shape, 1, turnShare);
        }
        Vec3 forward = axis.normalize();
        Vec3 side = side(forward);
        Vec3 lift = side.cross(forward);
        double turn = FULL_TURN * turnShare;
        double across = Math.cos(turn);
        double upDown = Math.sin(turn);
        double sideTan = Math.tan(Math.toRadians(shape.sideDegrees() * HALF));
        double liftTan = Math.tan(Math.toRadians(shape.liftDegrees() * HALF));
        double out = Math.min(sideTan / Math.max(Math.abs(across), NONE_THAT_WAY),
                liftTan / Math.max(Math.abs(upDown), NONE_THAT_WAY));
        return forward.add(side.scale(across * out)).add(lift.scale(upDown * out)).normalize();
    }

    /**
     * A direction inside a flat band: the turn share spreads it evenly across from one end to the other, and the
     * tilt share, an even draw squared back, spreads it evenly through the band's thin height.
     * operator report 2026-11-18: Line should spray a flat spread evenly in a horizontal line
     *
     * @param forward   the band's unit axis
     * @param side      the unit direction across
     * @param lift      the unit direction up and down
     * @param shape     the band's widths across and up and down
     * @param tiltShare the drip's tilt share, the square root of an even draw
     * @param turnShare the drip's turn share, an even draw
     * @return the unit direction
     */
    private static Vec3 bandDirection(Vec3 forward, Vec3 side, Vec3 lift, ConeShape shape, double tiltShare,
                                      double turnShare) {
        double across = Math.toRadians(shape.sideDegrees() * HALF) * fromEndToEnd(turnShare);
        double upDown = Math.toRadians(shape.liftDegrees() * HALF) * fromEndToEnd(tiltShare * tiltShare);
        return forward.add(side.scale(Math.tan(across))).add(lift.scale(Math.tan(upDown))).normalize();
    }

    /**
     * An even draw from zero to one, spread from one end of a band to the other.
     *
     * @param draw the draw in [0, 1]
     * @return the place across the band, -1 at one end to 1 at the other
     */
    private static double fromEndToEnd(double draw) {
        return (draw - HALF) / HALF;
    }
}
