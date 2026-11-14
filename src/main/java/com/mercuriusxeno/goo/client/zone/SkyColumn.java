package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The column rising from a Zone room's doorway, so the way out reads
 * from anywhere in the room: the doorway's footprint standing on its top,
 * running straight up past any far plane. Its surface is a window into
 * space with no depth of its own, so only the sides the camera sees draw,
 * at most two of the four from outside the footprint, each one quad. It
 * stands past any far plane, so each vertex draws moved along its own line
 * of sight to inside the farthest depth the pass draws: the picture is the
 * true column's, rising to the sky at any distance. The place reads from
 * the room's bounds alone, which the server syncs, so it holds where the
 * doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyColumn {

    /** The column's half width: half the doorway's one-block width. */
    static final double HALF_WIDTH = 0.5;
    /** How high over the doorway's top the column's top ring stands, past the far plane of any render distance. */
    static final double TOP_HEIGHT = 100_000;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    /** A square's corners, east then south signs, in order round it. */
    private static final double[][] SQUARE = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    /** Each side's outward direction, east then south, the side running from its corner in SQUARE to the next. */
    private static final double[][] OUTWARD = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};
    /** A quad's corners. */
    static final int CORNERS = 4;

    private SkyColumn() {
    }

    /**
     * The height the column rises from: the top of the doorway's upper cell,
     * the doorway standing on the center voidstone.
     *
     * @return the doorway's top in world blocks
     */
    public static double spoutHeight() {
        return ZoneRooms.ROOM_FLOOR_Y + 1 + DOORWAY_CELLS;
    }

    /**
     * The column's two rings in world blocks, the doorway's top first and
     * the column's top last, each ring the square's four corners in order
     * round it.
     *
     * @param bounds the room's boundary
     * @return the rings
     */
    public static List<List<Vec3>> rings(RoomExtent.Bounds bounds) {
        Vec3 axis = axisOf(bounds);
        double[] heights = {0, TOP_HEIGHT};
        List<List<Vec3>> rings = new ArrayList<>(heights.length);
        for (double height : heights) {
            List<Vec3> ring = new ArrayList<>(SQUARE.length);
            for (double[] corner : SQUARE) {
                ring.add(axis.add(HALF_WIDTH * corner[0], height, HALF_WIDTH * corner[1]));
            }
            rings.add(ring);
        }
        return rings;
    }

    /**
     * The quads of the sides a camera sees, each four corners in world
     * blocks wound to face the camera: the sides turned toward it from
     * outside the footprint, or all four seen from within where it stands
     * inside.
     *
     * @param bounds the room's boundary
     * @param camera the camera's place in world blocks
     * @return the quads
     */
    public static List<List<Vec3>> quads(RoomExtent.Bounds bounds, Vec3 camera) {
        List<Vec3> corners = new ArrayList<>();
        eachCorner(bounds, 0, 0, camera, (x, y, z) -> corners.add(new Vec3(x, y, z)));
        List<List<Vec3>> quads = new ArrayList<>(corners.size() / CORNERS);
        for (int start = 0; start < corners.size(); start += CORNERS) {
            quads.add(List.copyOf(corners.subList(start, start + CORNERS)));
        }
        return quads;
    }

    /**
     * Receives one corner of a column's quad in world blocks.
     */
    @FunctionalInterface
    interface CornerSink {
        /**
         * @param x the corner's east in world blocks
         * @param y the corner's height in world blocks
         * @param z the corner's south in world blocks
         */
        void corner(double x, double y, double z);
    }

    /**
     * Hands over the corners of the quads a camera sees on a column, quad by
     * quad as {@link #quads} lists them, the column moved by a copy's offset;
     * it builds nothing, so a frame's thousands of copies cost no garbage.
     *
     * @param bounds the room's boundary
     * @param east   the copy's offset east in blocks
     * @param south  the copy's offset south in blocks
     * @param camera the camera's place in world blocks
     * @param sink   receives each corner
     */
    static void eachCorner(RoomExtent.Bounds bounds, double east, double south, Vec3 camera, CornerSink sink) {
        double axisX = bounds.centerX() + HALF_BLOCK + east;
        double axisY = spoutHeight();
        double axisZ = bounds.centerZ() + HALF_BLOCK + south;
        double fromAxisX = camera.x() - axisX;
        double fromAxisZ = camera.z() - axisZ;
        boolean inside = true;
        for (double[] outward : OUTWARD) {
            inside &= fromAxisX * outward[0] + fromAxisZ * outward[1] <= HALF_WIDTH;
        }
        for (int side = 0; side < SQUARE.length; side++) {
            boolean seen = fromAxisX * OUTWARD[side][0] + fromAxisZ * OUTWARD[side][1] > HALF_WIDTH;
            if (!inside && !seen) {
                continue;
            }
            int next = (side + 1) % SQUARE.length;
            double first = inside ? 0 : TOP_HEIGHT;
            double last = TOP_HEIGHT - first;
            sink.corner(axisX + HALF_WIDTH * SQUARE[side][0], axisY + first, axisZ + HALF_WIDTH * SQUARE[side][1]);
            sink.corner(axisX + HALF_WIDTH * SQUARE[next][0], axisY + first, axisZ + HALF_WIDTH * SQUARE[next][1]);
            sink.corner(axisX + HALF_WIDTH * SQUARE[next][0], axisY + last, axisZ + HALF_WIDTH * SQUARE[next][1]);
            sink.corner(axisX + HALF_WIDTH * SQUARE[side][0], axisY + last, axisZ + HALF_WIDTH * SQUARE[side][1]);
        }
    }

    /**
     * The box the column stands in, from the doorway's top to the column's top.
     *
     * @param bounds the room's boundary
     * @return the box
     */
    public static AABB boxOf(RoomExtent.Bounds bounds) {
        Vec3 axis = axisOf(bounds);
        return new AABB(axis.x() - HALF_WIDTH, axis.y(), axis.z() - HALF_WIDTH, axis.x() + HALF_WIDTH,
                axis.y() + TOP_HEIGHT, axis.z() + HALF_WIDTH);
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
        return toTrue.scale(reachScale(toTrue.x(), toTrue.y(), toTrue.z(), farthest));
    }

    /**
     * How far along its line of sight a vertex draws: one where its true
     * place stands inside the reach, else the share of the way that ends at
     * the reach.
     *
     * @param east     the true place's east of the camera
     * @param up       the true place's height over the camera
     * @param south    the true place's south of the camera
     * @param farthest the farthest depth the pass draws
     * @return the scale from the true place to the drawn one
     */
    static double reachScale(double east, double up, double south, double farthest) {
        double distance = Math.sqrt(east * east + up * up + south * south);
        double reach = farthest * REACH_SHARE;
        return distance <= reach ? 1 : reach / distance;
    }

    private static Vec3 axisOf(RoomExtent.Bounds bounds) {
        return new Vec3(bounds.centerX() + HALF_BLOCK, spoutHeight(), bounds.centerZ() + HALF_BLOCK);
    }
}
