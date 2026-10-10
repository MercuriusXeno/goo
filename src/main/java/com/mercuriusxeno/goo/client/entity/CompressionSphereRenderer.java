package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws the compression sphere as a dark nether orb about a quarter block
 * wide, maroon-black with a faint nether glow at its rim, bobbing and
 * spinning like a dropped item: a camera-facing disc, so it reads round
 * from every side (decision black-hole-leaves-a-compression-sphere).
 */
public final class CompressionSphereRenderer extends EntityRenderer<CompressionSphere, EntityRenderState> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Goo.MODID, "textures/entity/compression_sphere.png");
    private static final RenderType RENDER_TYPE = GooSubmitter.translucentOn(TEXTURE);
    private static final float SIZE = 0.3f;
    private static final float HALF = 0.5f;
    private static final float LIFT = 0.15f;
    private static final float BOB_HEIGHT = 0.05f;
    private static final float BOB_RATE = 0.1f;
    private static final float SPIN_DEGREES_PER_TICK = 2f;
    /** The rim glow keeps the orb lit seven levels above its surroundings, as an experience orb does. */
    private static final int GLOW_LEVELS = 7;
    private static final int MAX_LIGHT = 15;
    private static final int OPAQUE = 255;
    private static final float SHADOW_RADIUS = 0.12f;

    /**
     * @param context the renderer context
     */
    public CompressionSphereRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = SHADOW_RADIUS;
    }

    @Override
    protected int getBlockLightLevel(CompressionSphere entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + GLOW_LEVELS, 0, MAX_LIGHT);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0, LIFT + Mth.sin(state.ageInTicks * BOB_RATE) * BOB_HEIGHT, 0);
        poseStack.mulPose(camera.orientation);
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.ageInTicks * SPIN_DEGREES_PER_TICK));
        poseStack.scale(SIZE, SIZE, SIZE);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> {
            vertex(buffer, pose, -HALF, -HALF, 0, 1, light);
            vertex(buffer, pose, HALF, -HALF, 1, 1, light);
            vertex(buffer, pose, HALF, HALF, 1, 0, light);
            vertex(buffer, pose, -HALF, HALF, 0, 0, light);
        });
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v,
                               int light) {
        buffer.addVertex(pose, x, y, 0)
                .setColor(OPAQUE, OPAQUE, OPAQUE, OPAQUE)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 1, 0);
    }
}
