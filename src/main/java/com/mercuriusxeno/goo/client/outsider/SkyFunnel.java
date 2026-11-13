package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The funnel rising from an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: a square column the doorway's width
 * standing on its top, holding that width up past half its height, then
 * curving out ever faster until it meets the room's edges at its ceiling,
 * above the build height, where it stops, its top the room's rectangle
 * covering the sky. Its half width grows with the inverse of the height left
 * below an asymptote standing just over the ceiling, the asymptote set per
 * room so the top ring lands on the room's edges. The funnel is sky-sized and
 * the doorway may stand past the far plane, so each vertex draws moved along
 * its own line of sight to inside the farthest depth the pass draws: the
 * picture is the true funnel's at any distance. The place reads from the
 * room's bounds alone, which the server syncs, so it holds where the
 * doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyFunnel {

    /** The column's half width at the doorway's top: half the doorway's one-block width. */
    static final double HALF_WIDTH = 0.5;
    /** The ceiling's height, above goo:pocket's build height of 256, where the funnel meets the room's edges. */
    static final double CEILING_Y = 512;
    /**
     * How much the column widens toward the ceiling, as a share of its
     * height: at half its height it stands no more than this share of the
     * height over the door's width, whatever the room.
     */
    private static final double PEEL_SHARE = 0.2;
    /** The rings from the doorway's top to the ceiling, less one. */
    static final int BANDS = 64;
    /** The quads along each side of a ring's square. */
    static final int SIDE_SEGMENTS = 4;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The brightness of the funnel's farthest point, its nearest drawing full. */
    static final float FARTHEST_BRIGHTNESS = 0.7F;
    /** The nearest distance the fade reads, so a vertex at the eye does not swallow the log's range. */
    private static final double NEAREST_FADE_DISTANCE = 1;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    private static final double HALF = 0.5;
    /** A square's corners, east then south signs, in order round it. */
    private static final double[][] SQUARE = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    private SkyFunnel() {
    }

    /**
     * The height the column rises from: the top of the doorway's upper cell,
     * the doorway standing on the center voidstone.
     *
     * @return the doorway's top in world blocks
     */
    public static double spoutHeight() {
        return OutsiderRooms.ROOM_FLOOR_Y + 1 + DOORWAY_CELLS;
    }

    /**
     * The funnel's half width at a height above the doorway's top in a room:
     * the doorway's half width there, growing with the inverse of the height
     * left below the room's asymptote, so it reaches the room's extent at the
     * ceiling.
     *
     * @param height blocks above the doorway's top, up to the ceiling
     * @param extent the room's extent
     * @return the half width in blocks
     */
    static double halfWidthAt(double height, double extent) {
        double asymptote = asymptoteOf(extent);
        return HALF_WIDTH + peel() * (1 / (asymptote - height) - 1 / asymptote);
    }

    /**
     * How high the ceiling stands over the doorway's top.
     *
     * @return the funnel's height in blocks
     */
    private static double reach() {
        return CEILING_Y - spoutHeight();
    }

    /**
     * The widening's scale, a share of the funnel's height.
     *
     * @return the scale in blocks
     */
    private static double peel() {
        return reach() * PEEL_SHARE;
    }

    /**
     * The height above the doorway's top the half width runs out without
     * bound at: just over the ceiling, by the root that lands the ceiling's
     * ring on the room's edges.
     *
     * @param extent the room's extent
     * @return the asymptote's height above the doorway's top
     */
    private static double asymptoteOf(double extent) {
        double reach = reach();
        double halfReach = reach * HALF;
        double widening = (extent - HALF_WIDTH) / peel();
        return reach - halfReach + Math.sqrt(halfReach * halfReach + reach / widening);
    }

    /**
     * The funnel's rings in world blocks, the doorway's top first and the
     * ceiling last, each ring's points running once round its square about
     * the axis through the room's center block. The gap to the asymptote
     * shrinks by the same share each band, so the long straight column takes
     * few rings and the curve many.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        double extent = bounds.extent();
        double asymptote = asymptoteOf(extent);
        double shrink = Math.pow((asymptote - reach()) / asymptote, 1.0 / BANDS);
        Vec3 axis = new Vec3(bounds.centerX() + HALF_BLOCK, spoutHeight(), bounds.centerZ() + HALF_BLOCK);
        List<List<Vec3>> rings = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            double height = band == BANDS ? reach() : asymptote - asymptote * Math.pow(shrink, band);
            rings.add(squareAt(axis, height, halfWidthAt(height, extent)));
        }
        return rings;
    }

    /**
     * One ring's square at a height, its points running once round it, the
     * square's corners among them.
     *
     * @param axis      the doorway's top at the room's center
     * @param height    blocks above the doorway's top
     * @param halfWidth the square's half width
     * @return the ring's points
     */
    private static List<Vec3> squareAt(Vec3 axis, double height, double halfWidth) {
        List<Vec3> ring = new ArrayList<>(SQUARE.length * SIDE_SEGMENTS);
        for (int side = 0; side < SQUARE.length; side++) {
            double[] from = SQUARE[side];
            double[] to = SQUARE[(side + 1) % SQUARE.length];
            for (int step = 0; step < SIDE_SEGMENTS; step++) {
                double along = (double) step / SIDE_SEGMENTS;
                ring.add(axis.add(halfWidth * (from[0] + (to[0] - from[0]) * along), height,
                        halfWidth * (from[1] + (to[1] - from[1]) * along)));
            }
        }
        return ring;
    }

    /**
     * The funnel's quads in world blocks, each four corners in order round
     * it, band by band from the doorway's top up.
     *
     * @param bounds the room's boundary
     * @return the quads
     */
    public static List<List<Vec3>> quads(RoomExtent.Bounds bounds) {
        List<List<Vec3>> rings = rings(bounds);
        List<List<Vec3>> quads = new ArrayList<>(BANDS * SQUARE.length * SIDE_SEGMENTS);
        for (int band = 0; band < BANDS; band++) {
            List<Vec3> lower = rings.get(band);
            List<Vec3> upper = rings.get(band + 1);
            for (int point = 0; point < lower.size(); point++) {
                int next = (point + 1) % lower.size();
                quads.add(List.of(lower.get(point), lower.get(next), upper.get(next), upper.get(point)));
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
     * A vertex's brightness by the log of its true distance from the camera,
     * measured within the funnel's own nearest and farthest points, so the
     * near column, the ceiling overhead and its far edge each read apart at
     * any distance, the farthest still bright enough to read.
     *
     * @param distance its true distance from the camera
     * @param nearest  the funnel's nearest true distance
     * @param farthest the funnel's farthest true distance
     * @return full at the nearest, FARTHEST_BRIGHTNESS at the farthest
     */
    public static float brightnessAt(double distance, double nearest, double farthest) {
        double near = Math.log(Math.max(nearest, NEAREST_FADE_DISTANCE));
        double span = Math.log(Math.max(farthest, NEAREST_FADE_DISTANCE)) - near;
        double nearToFar = span > 0 ? (Math.log(Math.max(distance, NEAREST_FADE_DISTANCE)) - near) / span : 0;
        return (float) (1 - (1 - FARTHEST_BRIGHTNESS) * nearToFar);
    }
}
