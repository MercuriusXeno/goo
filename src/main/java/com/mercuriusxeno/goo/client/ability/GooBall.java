package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import com.mercuriusxeno.goo.client.TypeBands;
import com.mercuriusxeno.goo.item.GooContents;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

/**
 * A ball of goo drawn as the crucible draws its goo: each type a layer over
 * the one before, on the goo surface shader that mingles the types by their
 * shares in drifting noise, each type's sprite wrapped about the ball and
 * mirrored across its back so the wrap leaves no seam.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class GooBall {

    /** How much further out each layer stands than the one under it, so they never fight. */
    static final float LAYER_STEP = 0.012f;
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
        RenderType surface = GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        for (TypeBand band : TypeBands.over(goo)) {
            GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(band.type()));
            int color = ARGB.color(alpha, GooSubmitter.fluidTint(band.type()));
            float scale = 1f + LAYER_STEP * band.layer();
            collector.submitCustomGeometry(poseStack, surface,
                    (pose, consumer) -> emitSphere(RenderContext.banded(pose, consumer, color, band), sprite, scale));
        }
    }

    private static void emitSphere(RenderContext ctx, GooRenderUtil.UvRect sprite, float scale) {
        for (Vector3f point : NetherSphereVisual.unitSphereMesh()) {
            float u = (float) (Math.abs(Math.atan2(point.z(), point.x())) / Math.PI);
            float v = point.y() * HALF + HALF;
            ctx.vertex(point.x() * scale, point.y() * scale, point.z() * scale,
                    sprite.u0() + (sprite.u1() - sprite.u0()) * u, sprite.v0() + (sprite.v1() - sprite.v0()) * v,
                    point.x(), point.y(), point.z());
        }
    }
}
