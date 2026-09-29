package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws concentric rings on the face of a block target the blob will strike,
 * where the aim arc ends, so the mark names the face as the outline names the
 * block (decision aim-arc-ends-in-face-bullseye).
 */
final class FaceBullseyeRenderer {
    /** How far the rings sit off the face along its normal, clear of the outline's fill. */
    static final double FACE_NUDGE = 0.01;
    /** Line segments each ring is drawn with. */
    static final int RING_SEGMENTS = 32;
    /** Alpha of a ring at birth on the additive glow lines, before it fades. */
    private static final int RING_PEAK_ALPHA = 160;

    private FaceBullseyeRenderer() {}

    /**
     * The face a target's bullseye marks: a block target's struck face, and
     * none for a chain marker, glow crystal or entity, which keep their own
     * highlight.
     *
     * @param target the aim target
     * @return the struck face, or null when the target draws no bullseye
     */
    static @Nullable Direction bullseyeFace(TargetResult target) {
        return target instanceof TargetResult.BlockTarget bt ? bt.face() : null;
    }

    /**
     * The points of one ring on the struck face, centered on the face center
     * and lying in the face's plane, nudged off the face along its normal.
     *
     * @param faceCenter the struck face's center
     * @param face       the struck face
     * @param radius     the ring's radius in blocks
     * @param segments   line segments around the ring
     * @return {@code segments + 1} points, the last closing the loop on the first
     */
    static Vec3[] ringPoints(Vec3 faceCenter, Direction face, double radius, int segments) {
        Vec3 center = faceCenter.add(face.getUnitVec3().scale(FACE_NUDGE));
        Direction.Axis normal = face.getAxis();
        Vec3 across = normal == Direction.Axis.X ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 along = normal == Direction.Axis.Z ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double angle = Math.TAU * i / segments;
            points[i] = center.add(across.scale(radius * Math.cos(angle)))
                    .add(along.scale(radius * Math.sin(angle)));
        }
        return points;
    }

    /**
     * Draws the bullseye on the target's struck face, anchored to the resolved
     * target rather than the sliding arc end, so it names the face the blob
     * will strike.
     *
     * @param poseStack    the pose stack
     * @param bufferSource the buffer source
     * @param camera       the render camera
     * @param target       the aim target; only a block target draws
     * @param rgb          the goo's highlight color
     * @param nowSeconds   seconds on the real-time clock the ripple runs on
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, TargetResult target, int rgb, double nowSeconds) {
        Direction face = bullseyeFace(target);
        if (face == null) {
            return;
        }
        Vec3 faceCenter = target.resolveEndpoint();
        Vec3 cam = camera.position();
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        for (double phase : RippleRings.ringPhases(nowSeconds)) {
            int alpha = (int) (RING_PEAK_ALPHA * RippleRings.ringOpacity(phase));
            if (RippleRings.isAlive(phase) && alpha > 0) {
                int color = ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
                Vec3[] ring = ringPoints(faceCenter, face, RippleRings.ringRadius(phase), RING_SEGMENTS);
                ctx.emitPolyline(cam, ring, color, width);
            }
        }
        bufferSource.endLastBatch();
    }
}
