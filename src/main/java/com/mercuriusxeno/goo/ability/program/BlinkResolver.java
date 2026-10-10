package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Resolves where a blink lands. Free aim sits a fixed range along the look:
 * where the body fits there it lands there, behind a wall or not, and where
 * the spot is inside a solid it shorts to the nearest face the look crosses.
 * A press begun on a block face pins that face's plane, and the landing
 * slides on the plane where the look crosses it, so pitch and yaw pick the
 * other two axes. Either way a spot the body does not fit backs off toward
 * where the blinker stands until it fits, and a blink with nowhere to go is
 * refused. The server's teleport and the client's cursor both call here.
 * Decision blink-lands-safely-costed-by-distance.
 * Decision ripple-outline-is-the-blink-cursor.
 */
public final class BlinkResolver {

    /** Blocks between the spots a backing-off landing tries. */
    static final double SAMPLE_STEP = 0.25;

    /** The shortest trip a blink makes; a landing nearer than this is no blink. */
    static final double MIN_TRAVEL = 0.5;

    /** The gap kept between the body and the face it stands against. */
    private static final double FACE_CLEARANCE = 1.0E-3;

    /** Half the side of the probe the trip's line is tested with for solids. */
    private static final double PROBE_HALF = 0.05;

    private static final double HALF = 0.5;

    /** Where along the look a plane parallel to it is crossed: never, read as behind the eye. */
    private static final double NO_CROSSING = -1;

    private BlinkResolver() {
    }

    /**
     * Where a blink lands.
     *
     * @param space the world as the blink reads it
     * @param feet  where the blinker's feet stand
     * @param look  the unit look vector
     * @param range the blink's range in blocks
     * @param body  the blinker's size
     * @param pin   the face plane the press began on, empty for free aim
     * @return the landing, empty when no spot the body fits lies on the way
     */
    public static Optional<BlinkLanding> resolve(BlinkSpace space, Vec3 feet, Vec3 look, double range,
            BlinkBody body, Optional<ChannelAim.FacePlane> pin) {
        Vec3 eye = feet.add(0, body.eyeHeight(), 0);
        Vec3 aimed = pin.map(plane -> onPinnedPlane(eye, look, range, plane, body))
                .orElseGet(() -> freeAim(space, feet, eye, look, range, body));
        return settle(space, feet, withinRange(feet, aimed, range), body);
    }

    /**
     * Pulls an aimed spot back toward the blinker to the range, so a plane
     * pinned at the press and walked away from never carries a blink past it.
     *
     * @param feet  where the blinker's feet stand
     * @param aimed the feet of the aimed spot
     * @param range the blink's range in blocks
     * @return the aimed spot, or the point the range along the line to it
     */
    static Vec3 withinRange(Vec3 feet, Vec3 aimed, double range) {
        Vec3 trip = aimed.subtract(feet);
        double length = trip.length();
        return length <= range ? aimed : feet.add(trip.scale(range / length));
    }

    /**
     * Where a blink snapped to an oculus lands: beside the oculus, in the
     * cell next to it nearest the blinker that the body fits in, the cells
     * around it and the one above it all tried.
     * Decision oculus-prism-becomes-a-hovering-eye.
     *
     * @param space the world as the blink reads it
     * @param feet  where the blinker's feet stand
     * @param node  the oculus's cell
     * @param body  the blinker's size
     * @return the landing, empty where the body fits beside the oculus nowhere
     */
    public static Optional<BlinkLanding> toNode(BlinkSpace space, Vec3 feet, BlockPos node, BlinkBody body) {
        return Stream.of(node.north(), node.south(), node.east(), node.west(), node.above())
                .map(Vec3::atBottomCenterOf)
                .filter(spot -> space.fits(body.boxAt(spot)))
                .min(Comparator.comparingDouble(feet::distanceTo))
                .map(spot -> new BlinkLanding(spot, feet.distanceTo(spot),
                        crossesSolid(space, body.centerAt(feet), body.centerAt(spot)), Optional.of(node)));
    }

    /**
     * The face a press pins: the first block face the look crosses within the
     * blink's range.
     *
     * @param space the world as the blink reads it
     * @param eye   the blinker's eye
     * @param look  the unit look vector
     * @param range the blink's range in blocks
     * @return the pinned face plane, empty when the look crosses no face in range
     */
    public static Optional<ChannelAim.FacePlane> pinAt(BlinkSpace space, Vec3 eye, Vec3 look, double range) {
        return space.firstFace(eye, eye.add(look.scale(range)))
                .map(hit -> new ChannelAim.FacePlane(hit.block(), hit.face()));
    }

    /**
     * The free-aim spot: the range along the look where the body fits there,
     * else standing against the first face the look crosses.
     *
     * @param space the world as the blink reads it
     * @param feet  where the blinker's feet stand
     * @param eye   the blinker's eye
     * @param look  the unit look vector
     * @param range the blink's range in blocks
     * @param body  the blinker's size
     * @return the feet of the aimed spot, which may not fit yet
     */
    private static Vec3 freeAim(BlinkSpace space, Vec3 feet, Vec3 eye, Vec3 look, double range, BlinkBody body) {
        Vec3 spot = feet.add(look.scale(range));
        if (space.fits(body.boxAt(spot))) {
            return spot;
        }
        return space.firstFace(eye, eye.add(look.scale(range)))
                .map(hit -> standAgainst(hit.point(), hit.face(), body))
                .orElse(spot);
    }

    /**
     * The spot on a pinned plane: where the look crosses the plane within the
     * range, else the range's end along the look laid onto the plane.
     *
     * @param eye   the blinker's eye
     * @param look  the unit look vector
     * @param range the blink's range in blocks
     * @param plane the pinned face plane
     * @param body  the blinker's size
     * @return the feet of the aimed spot, which may not fit yet
     */
    private static Vec3 onPinnedPlane(Vec3 eye, Vec3 look, double range, ChannelAim.FacePlane plane,
            BlinkBody body) {
        Direction face = plane.face();
        Direction.Axis axis = face.getAxis();
        double planeAt = plane.block().get(axis) + (face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? 1 : 0);
        double along = look.get(axis);
        double crossingAt = along == 0 ? NO_CROSSING : (planeAt - eye.get(axis)) / along;
        Vec3 crossing = crossingAt >= 0 && crossingAt <= range
                ? eye.add(look.scale(crossingAt))
                : eye.add(look.scale(range)).with(axis, planeAt);
        return standAgainst(crossing, face, body);
    }

    /**
     * The feet of a body standing against a face at a point on it: on a top
     * face, hanging under a bottom face, and out from a side face with the eye
     * at the point.
     *
     * @param point the point on the face
     * @param face  the side of the block the point lies on
     * @param body  the blinker's size
     * @return the feet
     */
    private static Vec3 standAgainst(Vec3 point, Direction face, BlinkBody body) {
        return switch (face) {
            case UP -> point.add(0, FACE_CLEARANCE, 0);
            case DOWN -> point.subtract(0, body.height() + FACE_CLEARANCE, 0);
            default -> point.add(Vec3.atLowerCornerOf(face.getUnitVec3i())
                    .scale(body.width() * HALF + FACE_CLEARANCE)).subtract(0, body.eyeHeight(), 0);
        };
    }

    /**
     * The first spot from the aimed one back toward the blinker the body fits
     * in, empty when every spot short of the shortest trip is solid.
     *
     * @param space the world as the blink reads it
     * @param feet  where the blinker's feet stand
     * @param aimed the feet of the aimed spot
     * @param body  the blinker's size
     * @return the landing, or empty
     */
    private static Optional<BlinkLanding> settle(BlinkSpace space, Vec3 feet, Vec3 aimed, BlinkBody body) {
        Vec3 back = feet.subtract(aimed);
        double length = back.length();
        for (double backed = 0; length - backed >= MIN_TRAVEL; backed += SAMPLE_STEP) {
            Vec3 spot = aimed.add(back.scale(backed / length));
            if (space.fits(body.boxAt(spot))) {
                return Optional.of(new BlinkLanding(spot, feet.distanceTo(spot),
                        crossesSolid(space, body.centerAt(feet), body.centerAt(spot))));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether the line between two points passes through a solid block,
     * probed every sample step.
     *
     * @param space the world as the blink reads it
     * @param from  where the line starts
     * @param to    where the line ends
     * @return true when a probe on the line meets a solid
     */
    private static boolean crossesSolid(BlinkSpace space, Vec3 from, Vec3 to) {
        Vec3 line = to.subtract(from);
        double length = line.length();
        for (double travelled = 0; travelled <= length; travelled += SAMPLE_STEP) {
            Vec3 probe = from.add(line.scale(travelled / length));
            if (!space.fits(new AABB(probe, probe).inflate(PROBE_HALF))) {
                return true;
            }
        }
        return false;
    }
}
