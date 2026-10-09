package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Where on its texture each point of an Unmake drink's skin is: a point on a
 * stream reads its place along the liquid, which rides the flow, and its arc
 * round the stream's spine from the path's own fixed frame, so the texture
 * rides the liquid unstretched and wraps the stream with no seam; a point on
 * the standing block reads two world axes square to the skin's facing, the
 * block's texture laid over it where it stands. A whole quad reads in one
 * frame, so no quad straddles two.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkTexture {

    /** The thinnest a stream reads round, in blocks, so the arc coordinate never collapses. */
    static final double THINNEST = 0.02;

    private DrinkTexture() {
    }

    /**
     * One point's place on the texture.
     *
     * @param along  blocks along the texture's first coordinate
     * @param around blocks along its second
     */
    public record Place(double along, double around) {
    }

    /**
     * @param skeleton the skeleton a point is nearest
     * @param point    the point
     * @return whether the point is on the skeleton's standing block rather than its stream
     */
    public static boolean onBlock(DrinkField.Skeleton skeleton, Vec3 point) {
        if (skeleton.box() == null) {
            return false;
        }
        double block = skeleton.box().signedDistance(point);
        for (int body = 0; body < skeleton.bodies() - 1; body++) {
            if (skeleton.distanceTo(body, point.x, point.y, point.z) < block) {
                return false;
            }
        }
        return true;
    }

    /**
     * A point's place on a stream's texture: along the liquid at the point's
     * foot on the stream's spine, and round the spine by arc length.
     *
     * @param skeleton the stream's skeleton
     * @param point    the point
     * @return its place
     */
    public static Place onStream(DrinkField.Skeleton skeleton, Vec3 point) {
        List<DrinkStream.Ring> rings = skeleton.rings();
        if (rings.size() < DrinkStream.FEWEST_RINGS) {
            return new Place(0, 0);
        }
        int nearest = 0;
        double foot = 0;
        double least = Double.MAX_VALUE;
        for (int segment = 0; segment + 1 < rings.size(); segment++) {
            double t = footOf(rings.get(segment).center(), rings.get(segment + 1).center(), point);
            double distance = rings.get(segment).center().lerp(rings.get(segment + 1).center(), t).distanceToSqr(point);
            if (distance < least) {
                least = distance;
                nearest = segment;
                foot = t;
            }
        }
        return placeBetween(skeleton.stream().path(), rings.get(nearest), rings.get(nearest + 1), foot, point);
    }

    private static Place placeBetween(DrinkStream.Path path, DrinkStream.Ring from, DrinkStream.Ring to, double foot,
                                      Vec3 point) {
        Vec3 offset = point.subtract(from.center().lerp(to.center(), foot));
        double angle = Math.atan2(offset.dot(DrinkStream.acrossOf(path)), offset.dot(DrinkStream.sideOf(path)));
        double radius = Math.max(THINNEST, from.radius() + (to.radius() - from.radius()) * foot);
        return new Place(from.material() + (to.material() - from.material()) * foot, angle * radius);
    }

    /**
     * @param from  a segment's start
     * @param to    its end
     * @param point a point
     * @return the share of the segment the point's foot falls at, 0 to 1
     */
    static double footOf(Vec3 from, Vec3 to, Vec3 point) {
        Vec3 line = to.subtract(from);
        return line.lengthSqr() == 0 ? 0 : Math.clamp(point.subtract(from).dot(line) / line.lengthSqr(), 0, 1);
    }

    /**
     * A point's place on the standing block's texture: the two world axes
     * square to the axis the skin most faces.
     *
     * @param point  the point
     * @param facing the normal the quad faces along, its four vertices' together
     * @return its place
     */
    public static Place onBlock(Vec3 point, Vec3 facing) {
        double ax = Math.abs(facing.x);
        double ay = Math.abs(facing.y);
        double az = Math.abs(facing.z);
        if (ay >= ax && ay >= az) {
            return new Place(point.x, point.z);
        }
        return ax >= az ? new Place(point.z, point.y) : new Place(point.x, point.y);
    }
}
