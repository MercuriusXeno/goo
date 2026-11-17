package com.mercuriusxeno.goo.client.particle;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/**
 * Where a drip's splat lies: in the plane of the face the drip struck, a
 * clearance out from it along the face's normal, so it shares no plane with
 * the surface under it; each splat in turn takes the next of a run of
 * clearances, so splats overlapping on one face share no plane with each
 * other either.
 * diagnose-and-correct-the-spray-splat-lying-flat-and-z-fighting
 * diagnose-then-fix-drip-z-fighting
 */
public final class SplatPlacement {

    /** The clearance between successive splats' planes, wide enough to hold apart in the depth buffer. */
    static final double CLEARANCE_STEP = 0.002;
    /** How many splats in turn stand at distinct clearances before the run repeats. */
    static final int CLEARANCE_STEPS = 12;

    /** The quad's own normal before it is turned onto a face: the way a camera-facing quad faces. */
    private static final Vector3f QUAD_NORMAL = new Vector3f(0, 0, 1);
    /** A unit quad's corners in its own plane, wound as the particle quad's are. */
    private static final List<Vector3f> UNIT_CORNERS = List.of(new Vector3f(1, -1, 0), new Vector3f(1, 1, 0),
            new Vector3f(-1, 1, 0), new Vector3f(-1, -1, 0));

    private SplatPlacement() {
    }

    /**
     * The face a splat was sent to lie on, read from the normal its spawn
     * carried as velocity: a splat sent with none, as the trail drip's
     * landing and a drip striking a living thing send it, lies on a top.
     *
     * @param normalX the struck face's normal, x
     * @param normalY the struck face's normal, y
     * @param normalZ the struck face's normal, z
     * @return the face
     */
    static Direction faceOf(double normalX, double normalY, double normalZ) {
        if (normalX == 0 && normalY == 0 && normalZ == 0) {
            return Direction.UP;
        }
        return Direction.getApproximateNearest(normalX, normalY, normalZ);
    }

    /**
     * How far a splat stands out from its face: the drip quad's surface
     * margin, then one step further for each splat in turn, through a run of
     * steps that then repeats.
     *
     * @param splat the splat's place in the order splats are laid
     * @return the clearance, in blocks
     */
    static double clearance(int splat) {
        return DripQuadPlacement.SURFACE_MARGIN + Math.floorMod(splat, CLEARANCE_STEPS) * CLEARANCE_STEP;
    }

    /**
     * Where the splat's quad is centered: the struck point, the clearance out along the face's normal.
     *
     * @param struck    the point the drip struck, on the face
     * @param face      the face struck
     * @param clearance how far out from the face the quad stands
     * @return the quad's center
     */
    static Vec3 center(Vec3 struck, Direction face, double clearance) {
        return struck.add(face.getUnitVec3().scale(clearance));
    }

    /**
     * The turn that lays the particle quad flat on a face, its front toward the open side.
     *
     * @param face the face struck
     * @return the rotation the quad draws under
     */
    static Quaternionf rotation(Direction face) {
        return new Quaternionf().rotationTo(QUAD_NORMAL, face.getUnitVec3f());
    }

    /**
     * The four corners the splat's quad draws at, as the particle quad turns
     * its unit corners by its rotation and scales them by its half size.
     *
     * @param struck    the point the drip struck, on the face
     * @param face      the face struck
     * @param clearance how far out from the face the quad stands
     * @param halfSize  the quad's half extent
     * @return the corners
     */
    static List<Vec3> corners(Vec3 struck, Direction face, double clearance, double halfSize) {
        Vec3 center = center(struck, face, clearance);
        Quaternionf rotation = rotation(face);
        return UNIT_CORNERS.stream().map(corner -> {
            Vector3f turned = rotation.transform(new Vector3f(corner)).mul((float) halfSize);
            return center.add(turned.x, turned.y, turned.z);
        }).toList();
    }
}
