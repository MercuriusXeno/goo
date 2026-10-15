package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Vector3f;

/**
 * A ball of goo drawn as the crucible draws its goo: one sphere on the goo
 * surface shader, which blends every type it holds by their shares in
 * drifting noise, each type's sprite wrapped about the ball and mirrored
 * across its back so the wrap leaves no seam.
 * decision unmake-waves-dissolve-by-crucible-cost
 * decision one-pass-blends-every-type
 */
public final class GooBall {

    private static final float HALF = 0.5f;

    private GooBall() {
    }

    /**
     * Submits a unit ball of goo about the pose's origin.
     *
     * @param poseStack the pose stack, scaled to the ball
     * @param collector the node collector
     * @param goo       the goo it holds
     * @param alpha     its alpha
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, GooContents goo, int alpha) {
        submitSphere(GooSubmitter.bandedSurfaces(poseStack, collector, alpha), goo);
    }

    /**
     * Submits the unit sphere once, carrying every type band the goo holds.
     *
     * @param submitter submits the surface carrying every band
     * @param goo       the goo the ball holds
     */
    static void submitSphere(BandedSurfaceSubmitter submitter, GooContents goo) {
        submitter.submit(TypeBands.over(goo), GooBall::emitSphere);
    }

    /**
     * Emits the unit sphere with sprite-relative UVs, mirrored across its back.
     *
     * @param ctx the render context the submitter built
     */
    private static void emitSphere(RenderContext ctx) {
        for (Vector3f point : NetherSphereVisual.unitSphereMesh()) {
            float u = (float) (Math.abs(Math.atan2(point.z(), point.x())) / Math.PI);
            float v = point.y() * HALF + HALF;
            ctx.vertex(point.x(), point.y(), point.z(), u, v, point.x(), point.y(), point.z());
        }
    }
}
