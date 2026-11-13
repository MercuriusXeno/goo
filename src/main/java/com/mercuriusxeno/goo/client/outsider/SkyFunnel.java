package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The funnel rising from an Outsider room's own doorway, so the way out
 * reads from anywhere in the room: a round pillar the door's width standing
 * on the doorway's top, holding that width almost all the way up to its
 * ceiling, then peeling out and expanding all the way out to the room's
 * edges, where it stops, its top the room's rectangle covering the sky. Its
 * radius grows with the inverse of the height left below the ceiling. Its
 * stars sit on its surface at a size in blocks, wrapped round the pillar by
 * its own width and laid flat across the ceiling, so they draw big near and
 * small far. The funnel is sky-sized and the doorway may stand past the far
 * plane, so each vertex draws moved along its own line of sight to inside
 * the farthest depth the pass draws: the picture is the true funnel's at any
 * distance. The place reads from the room's bounds alone, which the server
 * syncs, so it holds where the doorway's chunk stands unloaded.
 * decision sky-funnel-points-down-to-the-doorway-from-any-distance
 */
public final class SkyFunnel {

    /** The pillar's radius at the doorway's top: half the doorway's one-block width. */
    static final double HALF_WIDTH = 0.5;
    /** How high above the doorway's top the funnel's ceiling stands, where its radius runs without bound. */
    static final double CEILING = 1024;
    /** How soon below the ceiling the pillar peels out: the larger, the sooner and wider. */
    static final double PEEL = CEILING / 40;
    /** The rings from the doorway's top to the room's corners, less one. */
    static final int BANDS = 64;
    /** The quads round each band, a whole number of quarter turns so the room's corners fall on vertices. */
    static final int SEGMENTS = 48;
    /** The blocks one repeat of the stars spans round and up the pillar: once round its width. */
    public static final double PILLAR_BLOCKS_PER_REPEAT = Math.PI * 2 * HALF_WIDTH;
    /** The blocks one repeat of the stars spans across the ceiling, larger so far stars stay visible. */
    static final double CEILING_BLOCKS_PER_REPEAT = 32;
    /** The share of the farthest drawn depth a vertex is drawn within, clear of the far plane. */
    static final double REACH_SHARE = 0.9;
    /** The brightness of the funnel's farthest point, its nearest drawing full. */
    static final float FARTHEST_BRIGHTNESS = 0.3F;
    /** The nearest distance the fade reads, so a vertex at the eye does not swallow the log's range. */
    private static final double NEAREST_FADE_DISTANCE = 1;
    /** The doorway's cells stacked on the center voidstone. */
    private static final int DOORWAY_CELLS = 2;
    private static final double HALF_BLOCK = 0.5;
    private static final double FULL_TURN = Math.PI * 2;
    /** How far a square's corner stands from its middle, in extents: the top ring reaches it. */
    private static final double CORNER_REACH = Math.sqrt(2);

    private SkyFunnel() {
    }

    /**
     * One corner of the funnel's surface.
     *
     * @param place  its true place in world blocks
     * @param around the stars' coordinate across the surface
     * @param up     the stars' coordinate along the surface
     */
    public record Corner(Vec3 place, double around, double up) {
    }

    /**
     * The funnel's radius at a height above the doorway's top: the doorway's
     * half width there, barely wider until near the ceiling, then running
     * out without bound.
     *
     * @param height blocks above the doorway's top, below the ceiling
     * @return the radius in blocks
     */
    public static double radiusAt(double height) {
        return HALF_WIDTH + PEEL * (1 / (CEILING - height) - 1 / CEILING);
    }

    /**
     * The height the pillar rises from: the top of the doorway's upper cell,
     * the doorway standing on the center voidstone.
     *
     * @return the doorway's top in world blocks
     */
    public static double spoutHeight() {
        return OutsiderRooms.ROOM_FLOOR_Y + 1 + DOORWAY_CELLS;
    }

    /**
     * Whether the stars wrap round the funnel at a height, as on the pillar,
     * rather than lie flat across it, as on the ceiling: wrapped while the
     * wall stands steeper than it leans.
     *
     * @param height blocks above the doorway's top
     * @return true on the pillar
     */
    static boolean wrapsAsPillar(double height) {
        double gap = CEILING - height;
        return PEEL / (gap * gap) < 1;
    }

    /**
     * The funnel's rings in world blocks, the doorway's top first and the
     * room's corners last, each ring's points running once round the axis
     * through the room's center block.
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
     * from the doorway's top up.
     *
     * @param bounds the room's boundary
     * @return the quads
     */
    public static List<List<Corner>> quads(RoomExtent.Bounds bounds) {
        List<Double> heights = ringHeights(bounds);
        Vec3 axis = axisOf(bounds);
        List<List<Corner>> quads = new ArrayList<>(BANDS * SEGMENTS);
        for (int band = 0; band < BANDS; band++) {
            boolean pillar = wrapsAsPillar(heights.get(band));
            List<Corner> lower = ringAt(axis, heights.get(band), bounds.extent(), pillar);
            List<Corner> upper = ringAt(axis, heights.get(band + 1), bounds.extent(), pillar);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                quads.add(List.of(lower.get(segment), lower.get(segment + 1), upper.get(segment + 1),
                        upper.get(segment)));
            }
        }
        return quads;
    }

    /**
     * Each ring's corners once round and the first again at a full turn, so
     * the pillar's stars close on themselves rather than run back across the
     * last quad, each ring read by its own mapping.
     *
     * @param bounds the room's boundary
     * @return the rings of corners, the doorway's top first
     */
    private static List<List<Corner>> cornerRings(RoomExtent.Bounds bounds) {
        Vec3 axis = axisOf(bounds);
        return ringHeights(bounds).stream()
                .map(height -> ringAt(axis, height, bounds.extent(), wrapsAsPillar(height))).toList();
    }

    /**
     * The rings' heights above the doorway's top. The gap to the ceiling
     * shrinks by the same share each band out to the room's corners, so the
     * long straight pillar takes few rings and the flare many.
     *
     * @param bounds the room's boundary
     * @return the heights, the doorway's top first
     */
    private static List<Double> ringHeights(RoomExtent.Bounds bounds) {
        double topGap = 1 / ((bounds.extent() * CORNER_REACH - HALF_WIDTH) / PEEL + 1 / CEILING);
        double shrink = Math.pow(topGap / CEILING, 1.0 / BANDS);
        List<Double> heights = new ArrayList<>(BANDS + 1);
        for (int band = 0; band <= BANDS; band++) {
            heights.add(CEILING - CEILING * Math.pow(shrink, band));
        }
        return heights;
    }

    private static Vec3 axisOf(RoomExtent.Bounds bounds) {
        return new Vec3(bounds.centerX() + HALF_BLOCK, spoutHeight(), bounds.centerZ() + HALF_BLOCK);
    }

    /**
     * One ring's corners at a height, once round and the first again, each
     * stood on the room's edges where it would pass them, its stars wrapped
     * round the pillar or laid flat across the ceiling. A band reads both its
     * rings by its lower ring's mapping, so no quad mixes the two.
     *
     * @param axis   the doorway's top at the room's center
     * @param height blocks above the doorway's top
     * @param extent the room's extent
     * @param pillar whether the stars wrap round the pillar rather than lie flat
     * @return the ring's corners
     */
    private static List<Corner> ringAt(Vec3 axis, double height, double extent, boolean pillar) {
        double radius = radiusAt(height);
        List<Corner> ring = new ArrayList<>(SEGMENTS + 1);
        for (int segment = 0; segment <= SEGMENTS; segment++) {
            double turned = (double) segment / SEGMENTS;
            double east = radius * Math.cos(FULL_TURN * turned);
            double south = radius * Math.sin(FULL_TURN * turned);
            double inside = Math.min(1, extent / Math.max(Math.abs(east), Math.abs(south)));
            Vec3 place = axis.add(east * inside, height, south * inside);
            ring.add(pillar
                    ? new Corner(place, turned, height / PILLAR_BLOCKS_PER_REPEAT)
                    : new Corner(place, east * inside / CEILING_BLOCKS_PER_REPEAT,
                            south * inside / CEILING_BLOCKS_PER_REPEAT));
        }
        return ring;
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
     * near pillar, the ceiling overhead and the ceiling's far edge each read
     * apart at any distance.
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
