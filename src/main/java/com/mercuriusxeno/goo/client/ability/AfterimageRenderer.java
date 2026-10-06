package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.OptionalInt;

/**
 * Draws every live afterimage as the perimeter of its silhouettes. For each
 * ripple, the echoed body's cubes, grown by each silhouette's growth, fill
 * one color channel apiece of the ripple buffer, depth tested against a copy
 * of the world's depth; the edge pass then reads where each channel changes
 * and paints that perimeter over the frame in the goo type's color, as
 * strong as the silhouette's fade. Only the outline of the body's 2D
 * projection shows: the cubes' union fills the inside, so no part's own
 * edge crosses it.
 * Decision afterimage-is-one-shared-effect.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class AfterimageRenderer {

    /** Mask channels the ripple buffer holds, one per silhouette. */
    static final int CHANNELS = 4;

    /** The fullscreen triangle's vertex count. */
    private static final int FULLSCREEN_VERTICES = 3;

    /** The mask vertices' color, which the mask shader ignores for their alpha. */
    private static final int MASK_RGB = 0xFFFFFF;

    /** The edge pass's label in GPU debuggers. */
    private static final String PASS_LABEL = "Goo ripple perimeter";

    /** The uniform block carrying ColorModulator, the goo type's color. */
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";

    /** The sampler the edge pass reads the masks through. */
    private static final String MASK_SAMPLER = "InSampler";

    private AfterimageRenderer() {
    }

    /**
     * Draws the live afterimages after the level, dropping each whose ripple
     * has played out.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        drawRipples(event, Afterimages.CLIENT.live(mc.level.getGameTime()));
    }

    /**
     * Draws afterimages over the level as their ripples stand this frame.
     * The live list draws through here, and so does the blink cursor's
     * afterimage, standing at the destination
     * (decision ripple-outline-is-the-blink-cursor).
     *
     * @param event       the level render stage event
     * @param afterimages the afterimages to draw
     */
    public static void drawRipples(RenderLevelStageEvent.AfterLevel event,
            List<Afterimages.Afterimage<EntityRenderState>> afterimages) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || afterimages.isEmpty()) {
            return;
        }
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderTarget main = mc.getMainRenderTarget();
        RenderTarget ripple = RippleTarget.sizedTo(main);
        ripple.copyDepthFrom(main);
        CameraRenderState camera = event.getLevelRenderState().cameraRenderState;
        // AfterLevel fires once the level has popped its view rotation off the model view stack,
        // so the masks, laid in camera-relative world space, take the event's view matrix back.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        try {
            for (Afterimages.Afterimage<EntityRenderState> afterimage : afterimages) {
                drawRipple(mc, new PoseStack(), camera, afterimage, gameTime, main, ripple);
            }
        } finally {
            modelView.popMatrix();
        }
    }

    /**
     * Draws one ripple, where its silhouettes stand and its entity draws
     * through a living renderer: clears the mask channels, fills them, and
     * paints the perimeter over the frame.
     *
     * @param mc         the client
     * @param poseStack  a pose stack at the camera's position, its axes the world's
     * @param camera     the frame's camera
     * @param afterimage the afterimage
     * @param gameTime   the game time including the partial tick
     * @param main       the main render target
     * @param ripple     the ripple buffer
     */
    private static void drawRipple(Minecraft mc, PoseStack poseStack, CameraRenderState camera,
            Afterimages.Afterimage<EntityRenderState> afterimage, float gameTime, RenderTarget main,
            RenderTarget ripple) {
        List<Afterimages.Pulse> pulses = afterimage.pulses(gameTime);
        BodyPoseCapture body = captureBody(mc.getEntityRenderDispatcher(), afterimage, camera, poseStack);
        if (pulses.isEmpty() || body == null || body.rootPose() == null) {
            return;
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(ripple.getColorTexture(), 0);
        fillMasks(mc.renderBuffers().bufferSource(), body, pulses);
        paintPerimeter(main, ripple, afterimage.rgb());
    }

    /**
     * Submits the echoed entity's frozen state through its own renderer at
     * the ripple's point, keeping the body's pose.
     *
     * @param dispatcher the entity render dispatcher
     * @param afterimage the afterimage
     * @param camera     the frame's camera
     * @param poseStack  the level's pose stack
     * @return the capture, or null where the entity draws through no living renderer
     */
    private static @Nullable BodyPoseCapture captureBody(EntityRenderDispatcher dispatcher,
            Afterimages.Afterimage<EntityRenderState> afterimage, CameraRenderState camera, PoseStack poseStack) {
        EntityRenderState snapshot = afterimage.snapshot();
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(snapshot);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) {
            return null;
        }
        Model<?> model = living.getModel();
        BodyPoseCapture capture = new BodyPoseCapture(model);
        Vec3 fromCamera = afterimage.position().subtract(camera.pos);
        dispatcher.submit(snapshot, camera, fromCamera.x, fromCamera.y, fromCamera.z, poseStack, capture);
        capture.poseBody();
        return capture;
    }

    /**
     * Fills each standing silhouette's channel with the body's cubes grown
     * by its growth, the channel's value its fade.
     *
     * @param buffers the buffer source the masks fill through
     * @param body    the echoed body's pose
     * @param pulses  the ripple's standing silhouettes
     */
    private static void fillMasks(MultiBufferSource.BufferSource buffers, BodyPoseCapture body,
            List<Afterimages.Pulse> pulses) {
        List<ModelRay.CubeBox> cubes = ModelRay.cubesOf(body.body().root());
        for (int channel = 0; channel < Math.min(CHANNELS, pulses.size()); channel++) {
            Afterimages.Pulse pulse = pulses.get(channel);
            RenderType mask = GooRenderTypes.GOO_RIPPLE_MASK_TYPES.get(channel);
            VertexConsumer buffer = buffers.getBuffer(mask);
            int fade = ARGB.color(pulse.alpha(), MASK_RGB);
            for (ModelRay.CubeBox cube : cubes) {
                Matrix4f cubeToView = new Matrix4f(body.rootPose()).mul(new Matrix4f(cube.rootToCube()).invert());
                RippleMasks.fillGrownCube(buffer, cubeToView, cube.min(), cube.max(), pulse.growth(), fade);
            }
            buffers.endBatch(mask);
        }
    }

    /**
     * Paints the perimeter the edge pass reads off the masks over the frame.
     *
     * @param main   the main render target
     * @param ripple the ripple buffer holding the masks
     * @param rgb    the goo type's color
     */
    private static void paintPerimeter(RenderTarget main, RenderTarget ripple, int rgb) {
        GpuBufferSlice tint = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(),
                new Vector4f(ARGB.redFloat(rgb), ARGB.greenFloat(rgb), ARGB.blueFloat(rgb), 1f), new Vector3f(),
                new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> PASS_LABEL, main.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(GooRenderTypes.GOO_RIPPLE_EDGE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, tint);
            pass.bindTexture(MASK_SAMPLER, ripple.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, FULLSCREEN_VERTICES);
        }
    }
}
