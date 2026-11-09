package com.mercuriusxeno.goo.ability.quantum;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The cone a view sees through a gate's opening: from the seeing eye through
 * the opening's four edges, beyond the gate's face. Level content and nested
 * gates outside it are hidden by the frame around the opening, so a view
 * draws none of them.
 * nested-views-skip-when-tiny-or-outside-the-parent-opening
 *
 * @param bounds the planes bounding the cone, a point held where it stands on the inner side of every one
 */
public record ViewCone(List<Bound> bounds) {

    /**
     * One plane bounding a cone: a point is on its inner side where its dot
     * with the normal reaches the offset.
     *
     * @param normal the plane's inward normal
     * @param offset the inward normal's dot with any point on the plane
     */
    public record Bound(Vec3 normal, double offset) {
    }

    /**
     * The cone an eye sees through a gate's opening.
     *
     * @param eye  the seeing eye
     * @param gate the gate looked through
     * @return the cone
     */
    public static ViewCone through(Vec3 eye, Gate gate) {
        Vec3 center = gate.center();
        Vec3 right = gate.right().scale(gate.halfSpan(gate.right()));
        Vec3 up = gate.up().scale(gate.halfSpan(gate.up()));
        List<Vec3> corners = List.of(center.subtract(right).subtract(up), center.add(right).subtract(up),
                center.add(right).add(up), center.subtract(right).add(up));
        List<Bound> bounds = new ArrayList<>();
        for (int edge = 0; edge < corners.size(); edge++) {
            Vec3 normal = corners.get(edge).subtract(eye).cross(corners.get((edge + 1) % corners.size()).subtract(eye));
            // Each side plane holds the opening's middle on its inner side.
            Vec3 inward = normal.dot(center.subtract(eye)) < 0 ? normal.reverse() : normal;
            bounds.add(new Bound(inward, inward.dot(eye)));
        }
        // Beyond the gate's face from the eye: what stands on the eye's side of the opening is not seen through it.
        Vec3 facing = gate.face().getUnitVec3();
        Vec3 beyond = gate.out(eye) > 0 ? facing.reverse() : facing;
        bounds.add(new Bound(beyond, beyond.dot(center)));
        return new ViewCone(List.copyOf(bounds));
    }

    /**
     * Whether a box stands at least partly within the cone: no bound has the
     * whole box on its outer side. A box that crosses an edge of the cone's
     * corner may read held though none of it is, so the cone never culls what
     * it shows.
     *
     * @param box the box
     * @return true where some of it may be seen through the opening
     */
    public boolean holds(AABB box) {
        for (Bound bound : bounds) {
            Vec3 normal = bound.normal();
            Vec3 furthestIn = new Vec3(normal.x() >= 0 ? box.maxX : box.minX, normal.y() >= 0 ? box.maxY : box.minY,
                    normal.z() >= 0 ? box.maxZ : box.minZ);
            if (normal.dot(furthestIn) < bound.offset()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The cone both this and another bound.
     *
     * @param other the other cone
     * @return their intersection
     */
    public ViewCone and(ViewCone other) {
        List<Bound> both = new ArrayList<>(bounds);
        both.addAll(other.bounds());
        return new ViewCone(List.copyOf(both));
    }

    /**
     * The cone carried through a pair, so a view's cone reads in the space
     * the view within it is drawn from.
     *
     * @param transform the transform through the pair
     * @return the cone on the partner's side
     */
    public ViewCone through(PortalTransform transform) {
        return new ViewCone(bounds.stream().map(bound -> {
            Vec3 onPlane = bound.normal().scale(bound.offset() / bound.normal().lengthSqr());
            Vec3 normal = transform.direction(bound.normal());
            return new Bound(normal, normal.dot(transform.point(onPlane)));
        }).toList());
    }

    /**
     * Whether a nested gate shows through a parent gate's opening to an eye.
     *
     * @param eye       the seeing eye
     * @param parent    the gate looked through
     * @param candidate the nested gate
     * @return true where some of the candidate's blocks stand within the parent opening's cone
     */
    public static boolean showsThrough(Vec3 eye, Gate parent, Gate candidate) {
        return through(eye, parent).holds(candidate.blocks());
    }
}
