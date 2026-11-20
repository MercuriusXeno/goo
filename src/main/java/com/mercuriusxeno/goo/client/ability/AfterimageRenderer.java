package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

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
public final class AfterimageRenderer {

    /** Mask channels the ripple buffer holds, one per silhouette. */
    static final int CHANNELS = 4;

    /** The fullscreen triangle's vertex count. */
    private static final int FULLSCREEN_VERTICES = 3;

    /** A mask channel's fullest value, a body at full strength. */
    private static final int FULL_MASK = 0xFF;

    /** The mask vertices' color, which the mask shader ignores for their alpha. */
    private static final int MASK_RGB = 0xFFFFFF;

    /** The edge pass's label in GPU debuggers. */
    private static final String PASS_LABEL = "Goo ripple perimeter";

    /** The soft outline pass's label in GPU debuggers. */
    private static final String SOFT_PASS_LABEL = "Goo soft outline";

    /** The ripple buffer's cleared color, every channel empty. */
    private static final Vector4fc CLEAR_COLOR = new Vector4f(0, 0, 0, 0);

    /**
     * The value the cleared depth takes, past everything, so a body's mask fills through walls;
     * zero since 26.2 inverted depth and put the far plane there.
     */
    private static final double FARTHEST = 0.0;

    /** The uniform block carrying ColorModulator, the goo type's color. */
    private static final String TRANSFORMS_UNIFORM = "DynamicTransforms";

    /** The sampler the edge pass reads the masks through. */
    private static final String MASK_SAMPLER = "InSampler";

    private AfterimageRenderer() {
    }

    /**
     * Drops each afterimage whose ripple has played out and hands the shared
     * path one draw for the bodies' afterimages and one for the blocks',
     * each sorted by its nearest afterimage. The masks test a copy of the
     * level's depth; the perimeter then paints over the whole frame.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the afterimages' draws go
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        // It drew only at AfterLevel, which a portal view's pass never posts; the path runs AfterLevel routes there.
        if (PortalView.drawing()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 camera = mc.gameRenderer.mainCamera().position();
        List<Afterimages.Afterimage<EntityRenderState>> bodies = Afterimages.CLIENT.live(mc.level.getGameTime());
        if (!bodies.isEmpty()) {
            draws.accept(SortedDraw.nearestOf(bodies.stream().map(Afterimages.Afterimage::position), camera,
                    () -> drawRipples(frame, bodies)));
        }
        List<Afterimages.Afterimage<List<AABB>>> blocks = Afterimages.BLOCKS.live(mc.level.getGameTime());
        if (!blocks.isEmpty()) {
            draws.accept(SortedDraw.nearestOf(blocks.stream().map(Afterimages.Afterimage::position), camera,
                    () -> drawBlockRipples(frame, blocks)));
        }
    }

    /**
     * Draws afterimages over the level as their ripples stand this frame.
     * The live list draws through here, and so does the blink cursor's
     * afterimage, standing at the destination
     * (decision ripple-outline-is-the-blink-cursor).
     *
     * @param frame       the frame being drawn
     * @param afterimages the afterimages to draw
     */
    public static void drawRipples(LevelFrame frame, List<Afterimages.Afterimage<EntityRenderState>> afterimages) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || afterimages.isEmpty()) {
            return;
        }
        inRippleFrame(frame, ripples -> {
            for (Afterimages.Afterimage<EntityRenderState> afterimage : afterimages) {
                drawRipple(mc, new PoseStack(), ripples.camera(), afterimage, ripples.gameTime(), ripples.main(),
                        ripples.ripple());
            }
        });
    }

    /**
     * Draws blocks' afterimages over the level: each reaped plant's shape
     * boxes, grown by each silhouette's growth, rippling out of where it stood.
     * reap-breeze-harvests-and-replants
     *
     * @param frame       the frame being drawn
     * @param afterimages the blocks' afterimages to draw
     */
    public static void drawBlockRipples(LevelFrame frame, List<Afterimages.Afterimage<List<AABB>>> afterimages) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || afterimages.isEmpty()) {
            return;
        }
        inRippleFrame(frame, ripples -> {
            for (Afterimages.Afterimage<List<AABB>> afterimage : afterimages) {
                List<Afterimages.Pulse> pulses = afterimage.pulses(ripples.gameTime());
                if (pulses.isEmpty()) {
                    continue;
                }
                Vec3 corner = afterimage.position().subtract(ripples.camera().pos);
                Matrix4f boxToView = new Matrix4f().translation((float) corner.x, (float) corner.y, (float) corner.z);
                RenderSystem.getDevice().createCommandEncoder().clearColorTexture(ripples.ripple().getColorTexture(),
                        CLEAR_COLOR);
                fillBoxMasks(ImmediateBuffers.shared(), boxToView, afterimage.snapshot(), pulses);
                paintPerimeter(ripples.main(), ripples.ripple(), afterimage.rgb());
            }
        });
    }

    /**
     * One body drawn with a soft outline.
     *
     * @param snapshot the body's render state this frame
     * @param position the world point it stands at
     * @param strength how strongly its glow shows, zero to one
     */
    public record SoftOutline(EntityRenderState snapshot, Vec3 position, float strength) {
    }

    /**
     * Draws a soft blurred glow around bodies, seen through walls, each as
     * strong as its strength: every body fills the ripple buffer's red
     * channel at its strength, and one pass paints the blur of that mask
     * over the frame, spreading out from each body's edge.
     * decision sculker-sight-persists-where-echoes-have-touched
     *
     * @param frame    the frame being drawn
     * @param outlines the bodies to outline
     * @param rgb      the glow's color
     */
    public static void drawSoftOutlines(LevelFrame frame, List<SoftOutline> outlines, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || outlines.isEmpty()) {
            return;
        }
        inRippleFrame(frame, ripples -> {
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    ripples.ripple().getColorTexture(), CLEAR_COLOR, ripples.ripple().getDepthTexture(), FARTHEST);
            boolean drew = false;
            for (SoftOutline outline : outlines) {
                drew |= fillSoftMask(mc, ripples, outline);
            }
            if (drew) {
                paintThrough(GooRenderTypes.GOO_SOFT_OUTLINE, SOFT_PASS_LABEL, ripples.main(), ripples.ripple(), rgb);
            }
        });
    }

    /**
     * Fills the red channel with one body at its strength.
     *
     * @param mc      the client
     * @param frame   the frame's ripple state
     * @param outline the body
     * @return true where the body drew through a living renderer
     */
    private static boolean fillSoftMask(Minecraft mc, RippleFrame frame, SoftOutline outline) {
        BodyPoseCapture body = captureBody(mc.getEntityRenderDispatcher(), outline.snapshot(), outline.position(),
                frame.camera(), new PoseStack());
        if (body == null || body.rootPose() == null) {
            return false;
        }
        fillMasks(ImmediateBuffers.shared(), body,
                List.of(new Afterimages.Pulse(0f, Math.round(outline.strength() * FULL_MASK), 0f)));
        return true;
    }

    /**
     * What one frame's ripples draw against.
     *
     * @param camera   the frame's camera
     * @param gameTime the game time including the partial tick
     * @param main     the main render target
     * @param ripple   the ripple buffer, holding a copy of the world's depth
     */
    private record RippleFrame(CameraRenderState camera, float gameTime, RenderTarget main, RenderTarget ripple) {
    }

    /**
     * Readies the ripple buffer and the view a frame's ripples draw in, and draws them.
     *
     * @param frame  the frame being drawn
     * @param drawer draws the frame's ripples
     */
    private static void inRippleFrame(LevelFrame frame, Consumer<RippleFrame> drawer) {
        Minecraft mc = Minecraft.getInstance();
        float gameTime = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderTarget main = mc.gameRenderer.mainRenderTarget();
        RenderTarget ripple = RippleTarget.matching(main);
        ripple.copyDepthFrom(main);
        // The masks, laid in camera-relative world space, draw in the level's view. It is set, not multiplied
        // in: inside the shared path the stack's top is the view already, and a caller at AfterLevel outside the
        // path finds the level's view popped off it.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(frame.modelView());
        try {
            drawer.accept(new RippleFrame(frame.levelRenderState().cameraRenderState, gameTime, main, ripple));
        } finally {
            modelView.popMatrix();
        }
    }

    /**
     * Fills each standing silhouette's channel with a block's shape boxes
     * grown by its growth, the channel's value its fade.
     *
     * @param buffers   the buffer source the masks fill through
     * @param boxToView the transform from the block's cell into camera space
     * @param boxes     the block's shape boxes, about its cell's low corner
     * @param pulses    the ripple's standing silhouettes
     */
    private static void fillBoxMasks(ImmediateBuffers buffers, Matrix4f boxToView, List<AABB> boxes,
            List<Afterimages.Pulse> pulses) {
        for (int channel = 0; channel < Math.min(CHANNELS, pulses.size()); channel++) {
            Afterimages.Pulse pulse = pulses.get(channel);
            RenderType mask = GooRenderTypes.GOO_RIPPLE_MASK_TYPES.get(channel);
            VertexConsumer buffer = buffers.getBuffer(mask);
            int fade = ARGB.color(pulse.alpha(), MASK_RGB);
            for (AABB box : boxes) {
                RippleMasks.fillGrownCube(buffer, boxToView,
                        new Vector3f((float) box.minX, (float) box.minY, (float) box.minZ),
                        new Vector3f((float) box.maxX, (float) box.maxY, (float) box.maxZ), pulse.growth(), fade);
            }
            buffers.endBatch(mask);
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
        drawBody(mc, poseStack, camera, afterimage, afterimage.pulses(gameTime), main, ripple);
    }

    /**
     * Draws one body's silhouettes as the caller times them, over the level:
     * the aimed mob's mark ripples through here at its own pace and strength
     * (decision active-target-indicators-carry-depth-and-animation).
     *
     * @param frame    the frame being drawn
     * @param snapshot the body's render state this frame
     * @param position the world point it stands at
     * @param rgb      the color its perimeter paints in
     * @param pulses   the silhouettes standing this frame
     */
    public static void drawPulses(LevelFrame frame, EntityRenderState snapshot, Vec3 position, int rgb,
            List<Afterimages.Pulse> pulses) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || pulses.isEmpty()) {
            return;
        }
        Afterimages.Afterimage<EntityRenderState> body = new Afterimages.Afterimage<>(snapshot, position, rgb, 0L, 1);
        inRippleFrame(frame, ripples -> drawBody(mc, new PoseStack(), ripples.camera(), body, pulses, ripples.main(),
                ripples.ripple()));
    }

    /**
     * Draws a body's standing silhouettes where its entity draws through a
     * living renderer: clears the mask channels, fills them, and paints the
     * perimeter over the frame.
     *
     * @param mc        the client
     * @param poseStack a pose stack at the camera's position, its axes the world's
     * @param camera    the frame's camera
     * @param body      the body, where it stands and its color
     * @param pulses    the silhouettes standing this frame
     * @param main      the main render target
     * @param ripple    the ripple buffer
     */
    private static void drawBody(Minecraft mc, PoseStack poseStack, CameraRenderState camera,
            Afterimages.Afterimage<EntityRenderState> body, List<Afterimages.Pulse> pulses, RenderTarget main,
            RenderTarget ripple) {
        BodyPoseCapture pose = captureBody(mc.getEntityRenderDispatcher(), body.snapshot(), body.position(), camera,
                poseStack);
        if (pulses.isEmpty() || pose == null || pose.rootPose() == null) {
            return;
        }
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(ripple.getColorTexture(), CLEAR_COLOR);
        fillMasks(ImmediateBuffers.shared(), pose, pulses);
        paintPerimeter(main, ripple, body.rgb());
    }

    /**
     * Submits an entity's state through its own renderer at a point,
     * keeping the body's pose.
     *
     * @param dispatcher the entity render dispatcher
     * @param snapshot   the entity's render state
     * @param position   the world point it stands at
     * @param camera     the frame's camera
     * @param poseStack  the level's pose stack
     * @return the capture, or null where the entity draws through no living renderer
     */
    private static @Nullable BodyPoseCapture captureBody(EntityRenderDispatcher dispatcher,
            EntityRenderState snapshot, Vec3 position, CameraRenderState camera, PoseStack poseStack) {
        EntityRenderer<?, ?> renderer = dispatcher.getRenderer(snapshot);
        if (!(renderer instanceof LivingEntityRenderer<?, ?, ?> living)) {
            return null;
        }
        Model<?> model = living.getModel();
        BodyPoseCapture capture = new BodyPoseCapture(model);
        Vec3 fromCamera = position.subtract(camera.pos);
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
    private static void fillMasks(ImmediateBuffers buffers, BodyPoseCapture body,
            List<Afterimages.Pulse> pulses) {
        // diagnose-and-correct-the-floating-hat-brim-box: a part the skin leaves bare fills no box
        List<ModelRay.CubeBox> cubes = EncasedBodies.drawnCubes(body.body());
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
        paintThrough(GooRenderTypes.GOO_RIPPLE_EDGE, PASS_LABEL, main, ripple, rgb);
    }

    /**
     * Paints a fullscreen pass that reads the ripple buffer over the frame.
     *
     * @param pipeline the pass's pipeline
     * @param label    the pass's label in GPU debuggers
     * @param main     the main render target
     * @param ripple   the ripple buffer holding the masks
     * @param rgb      the color the pass paints in
     */
    private static void paintThrough(RenderPipeline pipeline, String label, RenderTarget main, RenderTarget ripple,
            int rgb) {
        GpuBufferSlice tint = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(),
                new Vector4f(ARGB.redFloat(rgb), ARGB.greenFloat(rgb), ARGB.blueFloat(rgb), 1f), new Vector3f(),
                new Matrix4f());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> label, main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS_UNIFORM, tint);
            pass.bindTexture(MASK_SAMPLER, ripple.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(FULLSCREEN_VERTICES, 1, 0, 0);
        }
    }
}
