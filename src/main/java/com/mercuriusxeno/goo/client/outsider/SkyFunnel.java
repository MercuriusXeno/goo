package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The funnel standing over an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: a horn, wide and nearly flat high in the
 * sky, its sides curving inward and steepening into a narrow spout on the
 * doorway's top. Its radius grows with the square of the height above the
 * spout, a parabola split in half with its sides inverted, revolved about
 * the vertical axis through the room's center block. The funnel is sky-sized
 * and the doorway may stand past the far plane, so each vertex draws moved
 * along its own line of sight to inside the farthest depth the pass draws:
 * the picture is the true funnel's at any distance. The place reads from the
 * room's bounds alone, which the server syncs, so it holds where the
 * doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyFunnel {

    /** The spout's radius on the doorway's top, half the doorway's width. */
    static final double SPOUT_RADIUS = 0.5;
    /** How high above the spout the horn's mouth stands. */
    static final double HEIGHT = 160;
    /** The horn's radius at its mouth. */
    static final double MOUTH_RADIUS = 96;
    /** How fast the radius grows with the square of the height. */
    static final double FLARE = (MOUTH_RADIUS - SPOUT_RADIUS) / (HEIGHT * HEIGHT);
    /** The rings between spout and mouth, less one. */
    static final int BANDS = 32;
    /** The quads round each band. */
    static final int SEGMENTS = 32;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    private static final double FULL_TURN = Math.PI * 2;

    private SkyFunnel() {
    }

    /**
     * The horn's radius at a height above the spout.
     *
     * @param height blocks above the doorway's top
     * @return the radius in blocks
     */
    public static double radiusAt(double height) {
        return SPOUT_RADIUS + FLARE * height * height;
    }

    /**
     * The height the spout stands at: the top of the doorway's upper cell,
     * the doorway standing on the center voidstone.
     *
     * @return the spout's y in world blocks
     */
    public static double spoutHeight() {
        return OutsiderRooms.ROOM_FLOOR_Y + 1 + DOORWAY_CELLS;
    }

    /**
     * The funnel's rings in world blocks, spout first and mouth last, each
     * ring's points running once round the axis through the room's center block.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        double axisX = bounds.centerX() + HALF_BLOCK;
        double axisZ = bounds.centerZ() + HALF_BLOCK;
        List<List<Vec3>> rings = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            double height = HEIGHT * band / BANDS;
            double radius = radiusAt(height);
            List<Vec3> ring = new ArrayList<>(SEGMENTS);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                double angle = FULL_TURN * segment / SEGMENTS;
                ring.add(new Vec3(axisX + radius * Math.cos(angle), spoutHeight() + height,
                        axisZ + radius * Math.sin(angle)));
            }
            rings.add(ring);
        }
        return rings;
    }

    /**
     * The funnel's quads in world blocks, each four corners in order round
     * it, band by band from the spout up.
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
}
