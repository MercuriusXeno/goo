package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The funnel standing over an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: a parabola split in half with its sides
 * inverted, revolved about the vertical axis through the room's center
 * block. Its height above the doorway is a downward parabola of its radius,
 * so its walls meet in a point on the doorway's top at an angle and flatten
 * to level at its rim. The funnel is sky-sized and the doorway may stand
 * past the far plane, so each vertex draws moved along its own line of
 * sight to inside the farthest depth the pass draws: the picture is the
 * true funnel's at any distance. The place reads from the room's bounds
 * alone, which the server syncs, so it holds where the doorway's chunk
 * stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyFunnel {

    /** How high above the doorway's top the rim stands. */
    static final double HEIGHT = 160;
    /** The rim's radius, where the walls stand level. */
    static final double RIM_RADIUS = 96;
    /** The rings from the point out to the rim, less one, evenly spaced by radius. */
    static final int BANDS = 32;
    /** The quads round each band. */
    static final int SEGMENTS = 48;
    /** How many times the starfield repeats round the funnel, whole so it meets itself. */
    static final int AROUND_REPEATS = 12;
    /** How many blocks of height one repeat of the starfield spans up the funnel. */
    static final double BLOCKS_PER_REPEAT_UP = 16;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The brightness of the funnel's farthest point, its nearest drawing full. */
    static final float FARTHEST_BRIGHTNESS = 0.3F;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    private static final double FULL_TURN = Math.PI * 2;

    private SkyFunnel() {
    }

    /**
     * One corner of the funnel's surface.
     *
     * @param place  its true place in world blocks
     * @param around the starfield's coordinate round the funnel
     * @param up     the starfield's coordinate up the funnel
     */
    public record Corner(Vec3 place, double around, double up) {
    }

    /**
     * The funnel's height above the doorway's top at a radius: zero at the
     * point, rising steepest there and levelling off at the rim.
     *
     * @param radius blocks out from the axis, zero to the rim
     * @return the height in blocks
     */
    public static double heightAt(double radius) {
        double towardRim = 1 - radius / RIM_RADIUS;
        return HEIGHT * (1 - towardRim * towardRim);
    }

    /**
     * The height the point stands at: the top of the doorway's upper cell,
     * the doorway standing on the center voidstone.
     *
     * @return the point's y in world blocks
     */
    public static double spoutHeight() {
        return OutsiderRooms.ROOM_FLOOR_Y + 1 + DOORWAY_CELLS;
    }

    /**
     * The funnel's rings in world blocks, the point first and the rim last,
     * each ring's points running once round the axis through the room's
     * center block.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        return cornerRings(bounds).stream()
                .map(ring -> ring.subList(0, SEGMENTS).stream().map(Corner::place).toList()).toList();
    }

    /**
     * The funnel's quads, each four corners in order round it, band by band
     * from the point out.
     *
     * @param bounds the room's boundary
     * @return the quads
     */
    public static List<List<Corner>> quads(RoomExtent.Bounds bounds) {
        List<List<Corner>> rings = cornerRings(bounds);
        List<List<Corner>> quads = new ArrayList<>(BANDS * SEGMENTS);
        for (int band = 0; band < BANDS; band++) {
            List<Corner> inner = rings.get(band);
            List<Corner> outer = rings.get(band + 1);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                quads.add(List.of(inner.get(segment), inner.get(segment + 1), outer.get(segment + 1),
                        outer.get(segment)));
            }
        }
        return quads;
    }

    /**
     * Each ring's corners once round and the first again at a full turn, so
     * the starfield's last repeat closes on its first rather than running
     * back across the last quad.
     *
     * @param bounds the room's boundary
     * @return the rings of corners, the point first and the rim last
     */
    private static List<List<Corner>> cornerRings(RoomExtent.Bounds bounds) {
        double axisX = bounds.centerX() + HALF_BLOCK;
        double axisZ = bounds.centerZ() + HALF_BLOCK;
        List<List<Corner>> rings = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            double radius = RIM_RADIUS * band / BANDS;
            double height = heightAt(radius);
            List<Corner> ring = new ArrayList<>(SEGMENTS + 1);
            for (int segment = 0; segment <= SEGMENTS; segment++) {
                double turned = (double) segment / SEGMENTS;
                double angle = FULL_TURN * turned;
                ring.add(new Corner(new Vec3(axisX + radius * Math.cos(angle), spoutHeight() + height,
                        axisZ + radius * Math.sin(angle)), turned * AROUND_REPEATS, height / BLOCKS_PER_REPEAT_UP));
            }
            rings.add(ring);
        }
        return rings;
    }

    /**
     * Where a vertex draws from the camera: at its true place where that
     * stands inside the reach, else moved along its own line of sight to the
     * reach. A triangle whose corners each stay on their own rays covers the
     * same rays, so the picture holds.
     *
     * @param camera    the pass's camera
     * @param truePlace the vertex's true place in world blocks
     * @param farthest  the farthest depth the pass draws
     * @return the vertex's place relative to the camera
     */
    public static Vec3 fromCamera(Vec3 camera, Vec3 truePlace, double farthest) {
        Vec3 toTrue = truePlace.subtract(camera);
        double distance = toTrue.length();
        double reach = farthest * REACH_SHARE;
        return distance <= reach ? toTrue : toTrue.scale(reach / distance);
    }

    /**
     * A vertex's brightness by its true distance from the camera, measured
     * within the funnel's own nearest and farthest points, so the near walls
     * stand in front of the far ones at any distance.
     *
     * @param distance its true distance from the camera
     * @param nearest  the funnel's nearest true distance
     * @param farthest the funnel's farthest true distance
     * @return full at the nearest, FARTHEST_BRIGHTNESS at the farthest
     */
    public static float brightnessAt(double distance, double nearest, double farthest) {
        double span = farthest - nearest;
        double nearToFar = span > 0 ? (distance - nearest) / span : 0;
        return (float) (1 - (1 - FARTHEST_BRIGHTNESS) * nearToFar);
    }
}
