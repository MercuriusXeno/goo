package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The funnel standing on an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: the doorway is its pillar, a round column
 * the door's width standing from the floor with the door hidden in it,
 * holding that width almost all the way up to its ceiling, then peeling out
 * and expanding all the way out to cover the sky. Its radius grows with the
 * inverse of the height left below the ceiling, so it runs without bound
 * there; the rings stop at a radius past any far plane. The funnel is
 * sky-sized and the doorway may stand past the far plane, so each vertex
 * draws moved along its own line of sight to inside the farthest depth the
 * pass draws: the picture is the true funnel's at any distance. The place
 * reads from the room's bounds alone, which the server syncs, so it holds
 * where the doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyFunnel {

    /** The pillar's radius at the floor: half the doorway's one-block width. */
    static final double HALF_WIDTH = 0.5;
    /** How high above the floor the funnel's ceiling stands, where its radius runs without bound. */
    static final double CEILING = 1024;
    /** How soon below the ceiling the pillar peels out: the larger, the sooner and wider. */
    static final double PEEL = CEILING / 40;
    /** The radius the top ring stands at, past the far plane of any render distance. */
    static final double TOP_RADIUS = 100_000;
    /** The rings from the floor to the top, less one, spaced evenly in the log of the gap to the ceiling. */
    static final int BANDS = 64;
    /** The quads round each band. */
    static final int SEGMENTS = 48;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The brightness of the funnel's farthest point, its nearest drawing full. */
    static final float FARTHEST_BRIGHTNESS = 0.3F;
    private static final double HALF_BLOCK = 0.5;
    private static final double FULL_TURN = Math.PI * 2;

    private SkyFunnel() {
    }

    /**
     * The funnel's radius at a height above the floor: the doorway's half
     * width at the floor, barely wider until near the ceiling, then running
     * out without bound.
     *
     * @param height blocks above the floor, below the ceiling
     * @return the radius in blocks
     */
    public static double radiusAt(double height) {
        return HALF_WIDTH + PEEL * (1 / (CEILING - height) - 1 / CEILING);
    }

    /**
     * The height the pillar stands from: the floor the doorway stands on,
     * the top of the center voidstone.
     *
     * @return the floor's y in world blocks
     */
    public static double floorHeight() {
        return OutsiderRooms.ROOM_FLOOR_Y + 1;
    }

    /**
     * The funnel's rings in world blocks, the floor first and the top last,
     * each ring's points running once round the axis through the room's
     * center block. The gap to the ceiling shrinks by the same share each
     * band, so the long straight pillar takes few rings and the flare many.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        double axisX = bounds.centerX() + HALF_BLOCK;
        double axisZ = bounds.centerZ() + HALF_BLOCK;
        double topGap = 1 / ((TOP_RADIUS - HALF_WIDTH) / PEEL + 1 / CEILING);
        double shrink = Math.pow(topGap / CEILING, 1.0 / BANDS);
        List<List<Vec3>> rings = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            double height = CEILING - CEILING * Math.pow(shrink, band);
            double radius = radiusAt(height);
            List<Vec3> ring = new ArrayList<>(SEGMENTS);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double angle = FULL_TURN * segment / SEGMENTS;
                ring.add(new Vec3(axisX + radius * Math.cos(angle), floorHeight() + height,
                        axisZ + radius * Math.sin(angle)));
            }
            rings.add(ring);
        }
        return rings;
    }

    /**
     * The funnel's quads in world blocks, each four corners in order round
     * it, band by band from the floor up.
     *
     * @param bounds the room's boundary
     * @return the quads
     */
    public static List<List<Vec3>> quads(RoomExtent.Bounds bounds) {
        List<List<Vec3>> rings = rings(bounds);
        List<List<Vec3>> quads = new ArrayList<>(BANDS * SEGMENTS);
        for (int band = 0; band < BANDS; band++) {
            List<Vec3> lower = rings.get(band);
            List<Vec3> upper = rings.get(band + 1);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                int next = (segment + 1) % SEGMENTS;
                quads.add(List.of(lower.get(segment), lower.get(next), upper.get(next), upper.get(segment)));
            }
        }
        return quads;
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
