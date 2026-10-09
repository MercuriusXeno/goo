package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a block's cube flows into its stream under an Unmake drink, like
 * taffy: every slice of the cube moves toward the hand from the first tick,
 * the face toward the hand leaving the block's entry at the flow's pace as
 * the stream's head, the back creeping forward so it reaches the entry as the
 * drain ends, the slices between stretched evenly; the cube keeps its full
 * width to the entry, and past it the stream begins at the cube's own width
 * as a square and narrows and rounds into the stream over {@link #FUNNEL}
 * blocks, so block and stream are one pull with no step. Nothing shrinks in
 * place; the mesh is the block's own faces carried along.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkBody {

    /** Blocks of stream past the entry over which the block's matter narrows from the cube's width to the stream's. */
    public static final double FUNNEL = 2.5;
    /** The cube's half width, the funnel's mouth, in blocks. */
    static final double MOUTH = 0.5;
    private static final double TWO = 2;
    private static final double THREE = 3;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private DrinkBody() {
    }

    /**
     * Where a point of the block stands and faces as it flows.
     *
     * @param point  the point, in the world
     * @param normal the unit normal of the surface there
     */
    public record Place(Vec3 point, Vec3 normal) {
    }

    /**
     * The block's lump at a moment of its drain.
     *
     * @param path     the block's path
     * @param progress how far the drain has gone, 0 to 1
     * @param flowed   blocks the stream's head has flowed past the entry since the start
     */
    public record Lump(DrinkStream.Path path, double progress, double flowed) {

        /**
         * @return blocks along the path of the lump's back, creeping from the far side to the entry over the drain
         */
        public double back() {
            return progress * DrinkStream.BLOCK_SPAN;
        }

        /**
         * @return blocks along the path of the lump's front, the stream's head
         */
        public double front() {
            return DrinkStream.BLOCK_SPAN + flowed;
        }

        /**
         * @param along a slice's share of the cube's span, 0 at the far side to 1 at the near face
         * @return blocks along the path the slice stands at now, stretched evenly between the back and the front
         */
        public double distanceOf(double along) {
            return back() + along * (front() - back());
        }
    }

    /**
     * @param point a point of the cube, in the world, where it stood
     * @param path  the block's path
     * @return the point's share of the cube's span along the path, 0 at the far side to 1 at the near face
     */
    static double alongOf(Vec3 point, DrinkStream.Path path) {
        Vec3 along = path.to().subtract(path.from()).normalize();
        return Math.clamp(point.subtract(path.from()).dot(along), 0, DrinkStream.BLOCK_SPAN) / DrinkStream.BLOCK_SPAN;
    }

    /**
     * The half width of a block's matter at a distance along its route: the
     * cube's to the entry, narrowing to the stream's over the funnel.
     *
     * @param distance the distance along the route, in blocks
     * @param stream   the stream's own radius there
     * @return the half width there, in blocks
     */
    public static double widthAt(double distance, double stream) {
        return MOUTH + (stream - MOUTH) * roundnessAt(distance);
    }

    /**
     * @param distance a distance along the route, in blocks
     * @return how round the block's matter is there, 0 the cube's square at the entry to 1 a circle past the funnel
     */
    public static double roundnessAt(double distance) {
        return ease(Math.clamp((distance - DrinkStream.BLOCK_SPAN) / FUNNEL, 0, 1));
    }

    /**
     * Where a point of the cube stands as the lump flows: its slice carried
     * along the path, its cross-section the cube's own; a slice past the
     * entry stands at the entry, the stream drawing it from there.
     *
     * @param point  the point, in the world, where it stood
     * @param normal the unit normal of the cube's face there
     * @param lump   the lump
     * @return where it stands and faces
     */
    public static Place placeOf(Vec3 point, Vec3 normal, Lump lump) {
        DrinkStream.Path path = lump.path();
        Vec3 along = path.to().subtract(path.from()).normalize();
        Vec3 side = along.cross(UP);
        side = side.lengthSqr() > 0 ? side.normalize() : EAST;
        Vec3 across = along.cross(side);
        Vec3 offset = point.subtract(path.from());
        Vec3 radial = offset.subtract(along.scale(offset.dot(along)));
        double distance = Math.min(DrinkStream.BLOCK_SPAN, lump.distanceOf(alongOf(point, path)));
        Vec3 spine = path.spineAt(distance / path.length());
        return new Place(spine.add(side.scale(radial.dot(side))).add(across.scale(radial.dot(across))), normal);
    }

    private static double ease(double t) {
        return t * t * (THREE - TWO * t);
    }
}
