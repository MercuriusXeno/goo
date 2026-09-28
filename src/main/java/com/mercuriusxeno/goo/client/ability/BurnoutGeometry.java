package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;
import java.util.function.BiConsumer;

/**
 * The shapes burnout explosions draw about a marker's block, in block-local
 * coordinates: a sphere about the block center, and an annulus square to the
 * placed face's axis (decision elemental-explosion-per-type).
 */
final class BurnoutGeometry {

    /** Offset to the block center from the block's corner. */
    static final float BLOCK_CENTER = 0.5f;
    private static final double TWO_PI = 2 * Math.PI;

    /**
     * Colors one annulus vertex from where it sits on the ring.
     */
    @FunctionalInterface
    interface AnnulusColor {
        /**
         * @param angle the vertex's angle about the face axis, in radians
         * @param outer true on the outer edge, false on the inner
         * @return the packed ARGB color
         */
        int at(double angle, boolean outer);
    }

    private BurnoutGeometry() {
    }

    /**
     * Draws into one render type at the marker's block, camera relative,
     * then flushes that type so the explosion lands this frame.
     *
     * @param frame the frame being drawn
     * @param pos   the marker's block position
     * @param type  the explosion's render type
     * @param draw  emits the explosion's vertices in block-local coordinates
     */
    static void drawAtMarker(BurnoutFrame frame, BlockPos pos, RenderType type,
                             BiConsumer<PoseStack.Pose, VertexConsumer> draw) {
        PoseStack poseStack = frame.poseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - frame.camera().x, pos.getY() - frame.camera().y,
                pos.getZ() - frame.camera().z);
        draw.accept(poseStack.last(), frame.buffers().getBuffer(type));
        poseStack.popPose();
        frame.buffers().endBatch(type);
    }

    /**
     * Emits the unit sphere scaled to radius about the block center, each
     * vertex's normal the unit direction to it.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param radius the sphere's radius in blocks
     * @param color  the packed ARGB color
     */
    static void emitSphere(PoseStack.Pose pose, VertexConsumer c, float radius, int color) {
        emitSphere(pose, c, Direction.UP, 0f, radius, color);
    }

    /**
     * Emits the unit sphere scaled to radius about the block center shifted
     * along the face's step by lift, each vertex's normal the unit direction to it.
     *
     * @param pose   the pose entry
     * @param c      the vertex consumer
     * @param face   the placed face
     * @param lift   the shift from the block center along the face's step, in blocks
     * @param radius the sphere's radius in blocks
     * @param color  the packed ARGB color
     */
    static void emitSphere(PoseStack.Pose pose, VertexConsumer c, Direction face, float lift, float radius,
                           int color) {
        FlatQuadContext sphere = new FlatQuadContext(pose, c);
        float cx = BLOCK_CENTER + face.getStepX() * lift;
        float cy = BLOCK_CENTER + face.getStepY() * lift;
        float cz = BLOCK_CENTER + face.getStepZ() * lift;
        for (Vector3f v : NetherSphereVisual.unitSphereMesh()) {
            sphere.vertex(cx + v.x() * radius, cy + v.y() * radius, cz + v.z() * radius,
                    color, v.x(), v.y(), v.z());
        }
    }

    /**
     * Emits an annulus square to the face's axis, centered on the block
     * center shifted along the face's step by lift, each segment one quad.
     *
     * @param pose     the pose entry
     * @param c        the vertex consumer
     * @param face     the placed face
     * @param lift     the shift from the block center along the face's step, in blocks
     * @param inner    the inner radius in blocks, 0 for a full disc
     * @param outer    the outer radius in blocks
     * @param segments how many quads run around the annulus
     * @param color    colors each vertex from where it sits
     */
    static void emitAnnulus(PoseStack.Pose pose, VertexConsumer c, Direction face, float lift,
                            float inner, float outer, int segments, AnnulusColor color) {
        FlatQuadContext ring = new FlatQuadContext(pose, c);
        for (int i = 0; i < segments; i++) {
            double a0 = TWO_PI * i / segments;
            double a1 = TWO_PI * (i + 1) / segments;
            annulusVertex(ring, face, lift, a0, inner, color.at(a0, false));
            annulusVertex(ring, face, lift, a0, outer, color.at(a0, true));
            annulusVertex(ring, face, lift, a1, outer, color.at(a1, true));
            annulusVertex(ring, face, lift, a1, inner, color.at(a1, false));
        }
    }

    /**
     * Emits one annulus vertex at an angle and radius in the plane square to the face.
     *
     * @param ring   the quad emitter
     * @param face   the placed face
     * @param lift   the shift from the block center along the face's step
     * @param angle  the angle about the face axis in radians
     * @param radius the distance from the center in blocks
     * @param color  the packed color
     */
    private static void annulusVertex(FlatQuadContext ring, Direction face, float lift, double angle,
                                      float radius, int color) {
        float u = (float) Math.cos(angle) * radius;
        float v = (float) Math.sin(angle) * radius;
        float x = BLOCK_CENTER + face.getStepX() * lift;
        float y = BLOCK_CENTER + face.getStepY() * lift;
        float z = BLOCK_CENTER + face.getStepZ() * lift;
        switch (face.getAxis()) {
            case X -> {
                y += u;
                z += v;
            }
            case Y -> {
                x += u;
                z += v;
            }
            case Z -> {
                x += u;
                y += v;
            }
        }
        ring.vertex(x, y, z, color, face.getStepX(), face.getStepY(), face.getStepZ());
    }

    /**
     * Cubic ease-out: fast, then slow.
     *
     * @param t progress in [0, 1]
     * @return eased progress in [0, 1]
     */
    static float easeOutCubic(float t) {
        float inverse = 1f - t;
        return 1f - inverse * inverse * inverse;
    }
}
