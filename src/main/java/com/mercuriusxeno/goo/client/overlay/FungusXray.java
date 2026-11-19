package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The brush the through-wall overlays share: a block's own model and texture drawn full bright in a color, swollen
 * about its center, and the magenta halo's slow breath. Oculus, the ore sightings, Transmit's target marker and
 * Sculker's sense view draw through it.
 */
public final class FungusXray {

    /** The halo's magenta. */
    private static final int GLOW_RGB = 0xFF40C0;
    private static final float HALF = 0.5f;
    private static final int OPAQUE = 255;

    private FungusXray() {
    }

    /**
     * A quad instance drawing at full bright with no overlay, in a color.
     *
     * @param color the ARGB color the quads take
     * @return the instance
     */
    public static QuadInstance instance(int color) {
        QuadInstance instance = new QuadInstance();
        instance.setColor(color);
        instance.setLightCoords(GooSubmitter.fullbrightLight());
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        return instance;
    }

    /**
     * A slow breath between half and full strength.
     *
     * @param seconds        seconds on the real-time clock
     * @param pulsePerSecond breaths per second, in radians
     * @return the strength, half to one
     */
    static float pulse(float seconds, float pulsePerSecond) {
        return HALF + HALF * (HALF + HALF * Mth.sin(seconds * pulsePerSecond));
    }

    /**
     * The glow's magenta at a strength and a peak alpha.
     *
     * @param strength the pulse's strength, zero to one
     * @param alpha    the alpha at full strength, zero to one
     * @return the ARGB color
     */
    static int glowColor(float strength, float alpha) {
        return ARGB.color(Math.round(Mth.clamp(strength * alpha, 0f, 1f) * OPAQUE), GLOW_RGB);
    }

    /**
     * Draws a block's own baked quads, swollen about its center.
     *
     * @param mc        the client
     * @param poseStack the pose stack
     * @param consumer  the vertex consumer
     * @param camera    the camera's position
     * @param pos       the block
     * @param instance  the color, light and overlay the quads take
     * @param swell     the scale about the block's center, one for its own size
     */
    public static void drawBlock(Minecraft mc, PoseStack poseStack, VertexConsumer consumer, Vec3 camera,
                          BlockPos pos, QuadInstance instance, float swell) {
        BlockState state = mc.level.getBlockState(pos);
        BlockStateModel model = mc.getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> parts = new ArrayList<>();
        model.collectParts(mc.level, pos, state, RandomSource.create(pos.asLong()), parts);
        poseStack.pushPose();
        poseStack.translate(pos.getX() - camera.x + HALF, pos.getY() - camera.y + HALF, pos.getZ() - camera.z + HALF);
        poseStack.scale(swell, swell, swell);
        poseStack.translate(-HALF, -HALF, -HALF);
        for (BlockStateModelPart part : parts) {
            for (Direction side : Direction.values()) {
                part.getQuads(side).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, instance));
            }
            part.getQuads(null).forEach(quad -> consumer.putBakedQuad(poseStack.last(), quad, instance));
        }
        poseStack.popPose();
    }
}
