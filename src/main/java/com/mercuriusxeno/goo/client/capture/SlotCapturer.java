package com.mercuriusxeno.goo.client.capture;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import java.util.function.Consumer;

/**
 * Draws an item stack the way an inventory slot does, through the game's item renderer into an offscreen texture,
 * and reads the pixels back. It follows GuiRenderer's item-atlas pass: the GUI display context, block lighting for a
 * block in its slot pose and flat lighting for a flat item, at 64x64 for the 16x16 slot so the page's image stays
 * crisp. Runs on the render thread, outside any render pass.
 * decision images-are-slot-captures-taken-on-detection
 */
final class SlotCapturer implements AutoCloseable {
    static final int CAPTURE_SIZE = 64;
    private static final float HALF = CAPTURE_SIZE / 2.0F;
    private static final String LABEL = "goo slot capture";
    private static final float Z_NEAR = -1000.0F;
    private static final float Z_FAR = 1000.0F;
    private static final double CLEAR_DEPTH = 1.0;
    private static final int NO_OUTLINE = 0;
    private static final int RGBA_BYTES = 4;
    private static final int OPAQUE = 0xFF;
    private static final int CHANNEL_BITS = 8;
    private static final int CHANNEL_MASK = 0xFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int READBACK_USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;
    private static final int COLOR_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC
            | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
    private static final int DEPTH_USAGE = GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;

    private final GpuTexture color;
    private final GpuTextureView colorView;
    private final GpuTexture depth;
    private final GpuTextureView depthView;
    private final Projection projection = new Projection();
    private final ProjectionMatrixBuffer projectionBuffer = new ProjectionMatrixBuffer(LABEL);

    SlotCapturer() {
        GpuDevice device = RenderSystem.getDevice();
        color = device.createTexture(LABEL, COLOR_USAGE, TextureFormat.RGBA8, CAPTURE_SIZE, CAPTURE_SIZE, 1, 1);
        colorView = device.createTextureView(color);
        // The depth format follows the main target's, as NeoForge's picture-in-picture textures do.
        TextureFormat depthFormat = Minecraft.getInstance().getMainRenderTarget().getDepthTexture().getFormat();
        depth = device.createTexture(LABEL, DEPTH_USAGE, depthFormat, CAPTURE_SIZE, CAPTURE_SIZE, 1, 1);
        depthView = device.createTextureView(depth);
        projection.setupOrtho(Z_NEAR, Z_FAR, CAPTURE_SIZE, CAPTURE_SIZE, true);
    }

    /**
     * Draws the stack and queues the read back; the pixels arrive on a later frame, once the GPU has copied them.
     *
     * @param stack    the stack to draw
     * @param onPixels takes the captured image, upright and with straight alpha, and owns closing it
     */
    void capture(ItemStack stack, Consumer<NativeImage> onPixels) {
        draw(stack);
        readBack(onPixels);
    }

    private void draw(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        TrackingItemStackRenderState state = new TrackingItemStackRenderState();
        minecraft.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, minecraft.level,
                null, 0);
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(color, 0, depth, CLEAR_DEPTH);
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.ORTHOGRAPHIC);
        RenderSystem.outputColorTextureOverride = colorView;
        RenderSystem.outputDepthTextureOverride = depthView;
        try {
            submitInSlotPose(state, minecraft);
        } finally {
            RenderSystem.outputColorTextureOverride = null;
            RenderSystem.outputDepthTextureOverride = null;
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static void submitInSlotPose(TrackingItemStackRenderState state, Minecraft minecraft) {
        minecraft.gameRenderer.getLighting().setupFor(state.usesBlockLight()
                ? Lighting.Entry.ITEMS_3D : Lighting.Entry.ITEMS_FLAT);
        PoseStack pose = new PoseStack();
        pose.translate(HALF, HALF, 0.0F);
        pose.scale(CAPTURE_SIZE, -CAPTURE_SIZE, CAPTURE_SIZE);
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        state.submit(pose, features.getSubmitNodeStorage(), GooSubmitter.fullbrightLight(),
                OverlayTexture.NO_OVERLAY, NO_OUTLINE);
        features.renderAllFeatures();
        minecraft.renderBuffers().bufferSource().endBatch();
    }

    private void readBack(Consumer<NativeImage> onPixels) {
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> LABEL, READBACK_USAGE,
                (long) CAPTURE_SIZE * CAPTURE_SIZE * RGBA_BYTES);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.copyTextureToBuffer(color, buffer, 0L, () -> {
            NativeImage image = new NativeImage(CAPTURE_SIZE, CAPTURE_SIZE, false);
            try (buffer; GpuBuffer.MappedView view = encoder.mapBuffer(buffer, true, false)) {
                for (int row = 0; row < CAPTURE_SIZE; row++) {
                    for (int column = 0; column < CAPTURE_SIZE; column++) {
                        int abgr = view.data().getInt((column + row * CAPTURE_SIZE) * RGBA_BYTES);
                        // The texture's rows run bottom up; the image's run top down.
                        image.setPixelABGR(column, CAPTURE_SIZE - row - 1, straightAlpha(abgr));
                    }
                }
            }
            onPixels.accept(image);
        }, 0);
    }

    /**
     * The item renders blended over a clear texture, leaving each color multiplied by its alpha, which the GUI blits
     * as premultiplied; a PNG holds straight alpha.
     *
     * @param abgr a premultiplied pixel
     * @return the pixel with each color divided by its alpha
     */
    static int straightAlpha(int abgr) {
        int alpha = abgr >>> ALPHA_SHIFT;
        if (alpha == 0 || alpha == OPAQUE) {
            return abgr;
        }
        int straight = alpha << ALPHA_SHIFT;
        for (int shift = 0; shift < ALPHA_SHIFT; shift += CHANNEL_BITS) {
            int channel = (abgr >>> shift) & CHANNEL_MASK;
            straight |= Math.min(OPAQUE, channel * OPAQUE / alpha) << shift;
        }
        return straight;
    }

    @Override
    public void close() {
        colorView.close();
        color.close();
        depthView.close();
        depth.close();
        projectionBuffer.close();
    }
}
