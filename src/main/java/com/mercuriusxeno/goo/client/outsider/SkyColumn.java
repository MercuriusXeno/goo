package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The column rising from an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: a square of the doorway's footprint
 * standing on its top, running straight up past any far plane. It stands
 * past any far plane, so each vertex draws moved along its own line of
 * sight to inside the farthest depth the pass draws: the picture is the true
 * column's, rising to the sky at any distance. Its rings stand ever farther
 * apart going up, so the fade by distance runs smooth along it. The place
 * reads from the room's bounds alone, which the server syncs, so it holds
 * where the doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyColumn {

    /** The column's half width: half the doorway's one-block width. */
    static final double HALF_WIDTH = 0.5;
    /** How high over the doorway's top the column's top ring stands, past the far plane of any render distance. */
    static final double TOP_HEIGHT = 100_000;
    /** The rings from the doorway's top to the column's top, less one. */
    static final int BANDS = 32;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The brightness of the column's farthest point, its nearest drawing full. */
    static final float FARTHEST_BRIGHTNESS = 0.4F;
    /** The nearest distance the fade reads, so a vertex at the eye does not swallow the log's range. */
    private static final double NEAREST_FADE_DISTANCE = 1;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    /** A square's corners, east then south signs, in order round it. */
    private static final double[][] SQUARE = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};

    private SkyColumn() {
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
     * The column's rings in world blocks, the doorway's top first and the
     * column's top last, each ring the square's four corners in order round
     * it. One block more than each ring's height grows by the same share each
     * band, so the rings stand close by the doorway and far apart in the sky.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        Vec3 axis = new Vec3(bounds.centerX() + HALF_BLOCK, spoutHeight(), bounds.centerZ() + HALF_BLOCK);
        List<List<Vec3>> rings = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            double height = Math.pow(TOP_HEIGHT + 1, (double) band / BANDS) - 1;
            List<Vec3> ring = new ArrayList<>(SQUARE.length);
            for (double[] corner : SQUARE) {
                ring.add(axis.add(HALF_WIDTH * corner[0], height, HALF_WIDTH * corner[1]));
            }
            rings.add(ring);
        }
        return rings;
    }

    /**
     * The column's quads in world blocks, each four corners in order round
     * it, band by band from the doorway's top up.
     *
     * @param bounds the room's boundary
     * @return the quads
     */
    public static List<List<Vec3>> quads(RoomExtent.Bounds bounds) {
        List<List<Vec3>> rings = rings(bounds);
        List<List<Vec3>> quads = new ArrayList<>(BANDS * SQUARE.length);
        for (int band = 0; band < BANDS; band++) {
            List<Vec3> lower = rings.get(band);
            List<Vec3> upper = rings.get(band + 1);
            for (int side = 0; side < SQUARE.length; side++) {
                int next = (side + 1) % SQUARE.length;
                quads.add(List.of(lower.get(side), lower.get(next), upper.get(next), upper.get(side)));
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
     * measured within the column's own nearest and farthest points, so the
     * column dims as it rises away at any distance.
     *
     * @param distance its true distance from the camera
     * @param nearest  the column's nearest true distance
     * @param farthest the column's farthest true distance
     * @return full at the nearest, FARTHEST_BRIGHTNESS at the farthest
     */
    public static float brightnessAt(double distance, double nearest, double farthest) {
        double near = Math.log(Math.max(nearest, NEAREST_FADE_DISTANCE));
        double span = Math.log(Math.max(farthest, NEAREST_FADE_DISTANCE)) - near;
        double nearToFar = span > 0 ? (Math.log(Math.max(distance, NEAREST_FADE_DISTANCE)) - near) / span : 0;
        return (float) (1 - (1 - FARTHEST_BRIGHTNESS) * nearToFar);
    }
}
