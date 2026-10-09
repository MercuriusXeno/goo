package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;

/**
 * How a block's cube flows into its stream under an Unmake drink: the block
 * is a lump of thick liquid drawn forward along its path from the first tick,
 * its front already the stream's entry, its back still the cube's shape and
 * texture, lofting smoothly from the square to the round stream as one skin;
 * as it empties its back advances and shrinks, until at the drain's end the
 * whole of it has flowed into the stream and is its tail. Nothing is cut
 * away; the mesh is the block's own faces carried along.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkBody {

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
     * The ring of the stream at a share of the block's path.
     */
    @FunctionalInterface
    public interface RingAt {
        /**
         * @param share the share of the path
         * @return the ring there
         */
        DrinkStream.Ring at(double share);
    }

    /**
     * The block's lump at a moment of its drain.
     *
     * @param path     the block's path
     * @param progress how far the drain has gone, 0 to 1
     */
    public record Lump(DrinkStream.Path path, double progress) {

        /**
         * @return blocks along the path of the lump's back, advancing from the far side to the entry
         */
        public double back() {
            return progress * DrinkStream.BLOCK_SPAN;
        }

        /**
         * @return the lump's length, from its back to the entry
         */
        public double length() {
            return (1 - progress) * DrinkStream.BLOCK_SPAN;
        }

        /**
         * @return how big the cube's shape still is at the lump's back, 1 whole to 0 gone
         */
        public double size() {
            return Math.sqrt(1 - progress);
        }

        /**
         * @param point a point of the cube, in the world, where it stood
         * @return blocks along the path the point stands at now, between the back and the entry
         */
        public double distanceOf(Vec3 point) {
            return back() + alongOf(point, path) * length();
        }
    }

    /**
     * @param point a point of the cube, in the world, where it stood
     * @param path  the block's path
     * @return the point's share of the cube's span along the path, 0 at the far side to 1 at the entry
     */
    static double alongOf(Vec3 point, DrinkStream.Path path) {
        Vec3 along = path.to().subtract(path.from()).normalize();
        return Math.clamp(point.subtract(path.from()).dot(along), 0, DrinkStream.BLOCK_SPAN) / DrinkStream.BLOCK_SPAN;
    }

    /**
     * Where a point of the cube stands as the lump flows: carried to its
     * place between the back and the entry, its distance from the path's
     * middle lofted from the cube's shrunken shape at the back to the
     * stream's ring at the entry, and its frame from the path's straight
     * frame at the back, where the cube stands as it stood, to the ring's.
     *
     * @param point  the point, in the world, where it stood
     * @param normal the unit normal of the cube's face there
     * @param lump   the lump
     * @param rings  the stream's rings along the path
     * @return where it stands and faces
     */
    public static Place placeOf(Vec3 point, Vec3 normal, Lump lump, RingAt rings) {
        DrinkStream.Path path = lump.path();
        Vec3 along = path.to().subtract(path.from()).normalize();
        Vec3 side = along.cross(UP);
        side = side.lengthSqr() > 0 ? side.normalize() : EAST;
        Vec3 across = along.cross(side);
        Vec3 offset = point.subtract(path.from());
        Vec3 radial = offset.subtract(along.scale(offset.dot(along)));
        double angle = radial.lengthSqr() > 0 ? Math.atan2(radial.dot(across), radial.dot(side)) : 0;
        double loft = ease(alongOf(point, path));
        DrinkStream.Ring ring = rings.at(lump.distanceOf(point) / path.length());
        Vec3 straightOut = side.scale(Math.cos(angle)).add(across.scale(Math.sin(angle)));
        Vec3 out = unit(straightOut.lerp(ring.outAt(angle), loft), straightOut);
        double reach = radial.length() * lump.size() * (1 - loft) + ring.radius() * loft;
        return new Place(ring.center().add(out.scale(reach)), unit(normal.lerp(out, loft), out));
    }

    private static Vec3 unit(Vec3 vector, Vec3 fallback) {
        return vector.lengthSqr() > 0 ? vector.normalize() : fallback;
    }

    private static double ease(double t) {
        return t * t * (THREE - TWO * t);
    }
}
