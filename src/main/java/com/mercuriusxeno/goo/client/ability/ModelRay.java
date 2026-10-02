package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Casts a goo's aim against a posed mob model's cubes, so the splat lands on
 * the first part the aim meets, the snout before the body behind it, rather
 * than where the aim enters the mob's bounding box.
 * Decision shader-coat-on-every-mob-landing.
 */
final class ModelRay {

    /** Axes a box spans. */
    private static final int AXES = 3;

    private ModelRay() {
    }

    /**
     * One model cube as the ray meets it: the part it belongs to, the
     * transform from the model root's space into that part's own, and the
     * cube's box there in blocks.
     *
     * @param partPath   the path of the part the cube belongs to, as ModelPart.visit names it
     * @param rootToCube the inverse of the part's pose under the model root
     * @param min        the box's low corner in blocks
     * @param max        the box's high corner in blocks
     */
    record CubeBox(String partPath, Matrix4fc rootToCube, Vector3fc min, Vector3fc max) {
    }

    /**
     * Where a ray met the model: the part it struck and the point in that
     * part's own space, which turns with the part as the mob moves its head
     * or limbs.
     *
     * @param partPath  the path of the struck part, or null for the model root's own frame
     * @param partPoint the point in the struck part's own space
     */
    record PartHit(@Nullable String partPath, Vector3fc partPoint) {
    }

    /**
     * The boxes of every cube of a posed model, in its root's space.
     *
     * @param root the model's root part, already posed for the mob
     * @return the cube boxes
     */
    static List<CubeBox> cubesOf(ModelPart root) {
        List<CubeBox> boxes = new ArrayList<>();
        root.visit(new PoseStack(), (pose, partPath, cubeIndex, cube) -> {
            // The drawn vertices carry the cube's grow, which its min and max fields leave out.
            Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
            Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
            for (ModelPart.Polygon polygon : cube.polygons) {
                for (ModelPart.Vertex vertex : polygon.vertices()) {
                    Vector3f drawn = new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ());
                    min.min(drawn);
                    max.max(drawn);
                }
            }
            if (min.x <= max.x) {
                boxes.add(new CubeBox(partPath, new Matrix4f(pose.pose()).invert(), min, max));
            }
        });
        return boxes;
    }

    /**
     * A part's pose under the model root as the model stands posed now.
     *
     * @param root     the model's root part, already posed for the mob
     * @param partPath the part's path, as ModelPart.visit names it
     * @return the part's pose, or empty where the model holds no such part with cubes
     */
    static Optional<Matrix4f> partPose(ModelPart root, String partPath) {
        Matrix4f[] found = new Matrix4f[1];
        root.visit(new PoseStack(), (pose, path, cubeIndex, cube) -> {
            if (found[0] == null && path.equals(partPath)) {
                found[0] = new Matrix4f(pose.pose());
            }
        });
        return Optional.ofNullable(found[0]);
    }

    /**
     * The first part a ray meets among the boxes, ahead of its origin, and
     * where on it.
     *
     * @param boxes     the model's cube boxes
     * @param origin    the ray's origin in the model root's space
     * @param direction the ray's direction in the model root's space
     * @return the part met nearest and the point in its space, or empty where it misses every box
     */
    static Optional<PartHit> firstHit(List<CubeBox> boxes, Vector3fc origin, Vector3fc direction) {
        float nearest = Float.POSITIVE_INFINITY;
        CubeBox struck = null;
        for (CubeBox box : boxes) {
            Vector3f localOrigin = box.rootToCube().transformPosition(origin, new Vector3f());
            Vector3f localDirection = box.rootToCube().transformDirection(direction, new Vector3f());
            float entry = entryAlong(localOrigin, localDirection, box.min(), box.max());
            if (entry < nearest) {
                nearest = entry;
                struck = box;
            }
        }
        if (struck == null) {
            return Optional.empty();
        }
        Vector3f rootPoint = new Vector3f(direction).mul(nearest).add(origin);
        return Optional.of(new PartHit(struck.partPath(), struck.rootToCube().transformPosition(rootPoint,
                new Vector3f())));
    }

    /**
     * Where along a ray it enters a box, by the slab test: the latest of the
     * three axes' entries, so long as it comes before the earliest exit.
     * The parameter survives the affine move into the cube's space, so it
     * measures the same ray in the root's space.
     *
     * @param origin    the ray's origin in the box's space
     * @param direction the ray's direction in the box's space
     * @param min       the box's low corner
     * @param max       the box's high corner
     * @return the ray parameter of its entry ahead of the origin, or infinity where it misses
     */
    static float entryAlong(Vector3fc origin, Vector3fc direction, Vector3fc min, Vector3fc max) {
        float enter = 0f;
        float exit = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < AXES; axis++) {
            float o = origin.get(axis);
            float d = direction.get(axis);
            if (d == 0f) {
                if (o < min.get(axis) || o > max.get(axis)) {
                    return Float.POSITIVE_INFINITY;
                }
                continue;
            }
            float near = (min.get(axis) - o) / d;
            float far = (max.get(axis) - o) / d;
            enter = Math.max(enter, Math.min(near, far));
            exit = Math.min(exit, Math.max(near, far));
        }
        return enter <= exit ? enter : Float.POSITIVE_INFINITY;
    }
}
