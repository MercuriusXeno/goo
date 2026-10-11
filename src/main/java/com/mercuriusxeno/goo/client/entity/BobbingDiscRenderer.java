package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.client.GooSubmitter;
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
import net.minecraft.world.entity.Entity;

/**
 * Draws a pseudo-item entity as a camera-facing textured disc, bobbing and
 * spinning like a dropped item and lit some levels above its surroundings,
 * so it reads round from every side: the compression sphere a black hole
 * leaves, and the feed Jelly's Feed lays.
 *
 * @param <T> the entity drawn
 */
public class BobbingDiscRenderer<T extends Entity> extends EntityRenderer<T, EntityRenderState> {

    private static final float HALF = 0.5f;
    private static final float LIFT = 0.15f;
    private static final float BOB_HEIGHT = 0.05f;
    private static final float BOB_RATE = 0.1f;
    private static final float SPIN_DEGREES_PER_TICK = 2f;
    private static final int MAX_LIGHT = 15;
    private static final int OPAQUE = 255;
    /** The shadow spans this share of the disc's width. */
    private static final float SHADOW_SHARE = 0.4f;

    private final RenderType renderType;
    private final float size;
    private final int glowLevels;

    /**
     * @param context    the renderer context
     * @param texture    the disc's texture
     * @param size       the disc's width in blocks
     * @param glowLevels the light levels the disc stands lit above its surroundings
     */
    protected BobbingDiscRenderer(EntityRendererProvider.Context context, Identifier texture, float size,
                                  int glowLevels) {
        super(context);
        this.renderType = GooSubmitter.translucentOn(texture);
        this.size = size;
        this.glowLevels = glowLevels;
        this.shadowRadius = size * SHADOW_SHARE;
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos blockPos) {
        return Mth.clamp(super.getBlockLightLevel(entity, blockPos) + glowLevels, 0, MAX_LIGHT);
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
        poseStack.scale(size, size, size);
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
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
