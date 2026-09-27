package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Shared utilities for in-world HUD billboards rendered via nine-slice backgrounds.
 * Extracted from CrucibleHudRenderer for reuse by all machine HUD renderers.
 */
public final class InWorldHud {

    /**
     * Scale factor: 1 pixel = 1/64 of a block.
     */
    public static final float PIXEL_SCALE = 1f / 64f;
    /**
     * Border inset in scaled pixels for nine-slice rendering.
     */
    public static final float BORDER = NineSlice.BORDER;
    /**
     * Z offset for content (icons, text) to sit in front of the background.
     */
    public static final float CONTENT_Z = 1f;
    /**
     * Background texture: vanilla HUD effect background.
     */
    public static final Identifier BG_TEXTURE = Identifier.withDefaultNamespace(
            "textures/gui/sprites/hud/effect_background.png");
    /**
     * Full white color for quad rendering.
     */
    public static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /**
     * Degrees-to-radians offset for camera yaw (faces player).
     */
    private static final float YAW_OFFSET = 180;
    /**
     * Half divisor for centering calculations.
     */
    private static final float HALF = 2f;
    /**
     * Default frame delta-time when no previous frame exists.
     */
    private static final float DEFAULT_DT = 0.016f;
    /**
     * Nanoseconds per second for delta-time conversion.
     */
    private static final float NANOS_PER_SECOND = 1_000_000_000f;
    /**
     * Maximum delta-time clamp to handle lag spikes.
     */
    private static final float MAX_DT = 0.1f;


    private InWorldHud() {
    }

    /**
     * Renders a nine-slice background using the vanilla effect_background texture.
     * The 3px border is never stretched; edges stretch in one axis; center stretches freely.
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source for rendering
     * @param rect      the panel rectangle (position + size)
     * @param color     the ARGB tint, whose alpha fades the background
     */
    public static void renderBackground(PoseStack poseStack, MultiBufferSource buffers,
                                        PanelRectangle rect, int color) {
        renderBackgroundInternal(poseStack, buffers, rect, false, color);
    }

    /**
     * Renders a nine-slice background without depth testing (renders on top of world).
     *
     * @param poseStack the pose stack for rendering
     * @param buffers   the buffer source for rendering
     * @param rect      the panel rectangle (position + size)
     */
    public static void renderBackgroundSeeThrough(PoseStack poseStack, MultiBufferSource buffers,
                                                  PanelRectangle rect) {
        renderBackgroundInternal(poseStack, buffers, rect, true, OPAQUE_WHITE);
    }

    /**
     * Internal nine-slice background renderer with optional see-through mode.
     *
     * @param poseStack  the pose stack for rendering
     * @param buffers    the buffer source for rendering
     * @param rect       the panel rectangle (position + size)
     * @param seeThrough whether to disable depth testing
     * @param color      the ARGB tint of every quad
     */
    private static void renderBackgroundInternal(PoseStack poseStack, MultiBufferSource buffers,
                                                 PanelRectangle rect, boolean seeThrough, int color) {
        VertexConsumer vc = buffers.getBuffer(
                seeThrough ? RenderTypes.textSeeThrough(BG_TEXTURE) : RenderTypes.text(BG_TEXTURE));
        PoseStack.Pose pose = poseStack.last();
        for (NineSlice.Slice s : NineSlice.of(rect)) {
            nineSliceQuad(vc, pose, s, color);
        }
    }

    /**
     * Emits one quad of the nine-slice background at z=0.
     *
     * @param vc    the vertex consumer
     * @param pose  the pose matrix entry
     * @param s     the slice's pixel rectangle and texture region
     * @param color the ARGB tint of the quad
     */
    private static void nineSliceQuad(VertexConsumer vc, PoseStack.Pose pose, NineSlice.Slice s, int color) {
        iconVertex(vc, pose, s.x0(), s.y0(), 0f, s.u0(), s.v0(), color);
        iconVertex(vc, pose, s.x0(), s.y1(), 0f, s.u0(), s.v1(), color);
        iconVertex(vc, pose, s.x1(), s.y1(), 0f, s.u1(), s.v1(), color);
        iconVertex(vc, pose, s.x1(), s.y0(), 0f, s.u1(), s.v0(), color);
    }

    /**
     * Adds a vertex with full-bright lighting at the given depth.
     *
     * @param vc    the vertex consumer
     * @param pose  the pose matrix entry
     * @param x     the X coordinate
     * @param y     the Y coordinate
     * @param z     the Z coordinate
     * @param u     the U texture coordinate
     * @param v     the V texture coordinate
     * @param color the ARGB tint, whose alpha fades the vertex
     */
    public static void iconVertex(VertexConsumer vc, PoseStack.Pose pose,
                                  float x, float y, float z, float u, float v, int color) {
        vc.addVertex(pose, x, y, z)
                .setColor(color)
                .setUv(u, v)
                .setLight(GooSubmitter.fullbrightLight());
    }

    /**
     * Draws text at the content Z depth (in front of background).
     *
     * @param font      the font renderer
     * @param buffers   the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param text      the text string to render
     * @param x         the X coordinate
     * @param y         the Y coordinate
     * @param color     the ARGB color value
     */
    public static void drawText(Font font, MultiBufferSource buffers,
                                PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.NORMAL);
    }

    /**
     * Draws text without depth testing (renders on top of world).
     *
     * @param font      the font renderer
     * @param buffers   the buffer source for rendering
     * @param poseStack the pose stack for rendering
     * @param text      the text string to render
     * @param x         the X coordinate
     * @param y         the Y coordinate
     * @param color     the ARGB color value
     */
    public static void drawTextSeeThrough(Font font, MultiBufferSource buffers,
                                          PoseStack poseStack, String text, float x, float y, int color) {
        drawTextInternal(font, buffers, poseStack, text, x, y, color, Font.DisplayMode.SEE_THROUGH);
    }

    /**
     * Internal text renderer with configurable display mode.
     *
     * @param font        the font renderer
     * @param buffers     the buffer source for rendering
     * @param poseStack   the pose stack for rendering
     * @param text        the text string to render
     * @param x           the X coordinate
     * @param y           the Y coordinate
     * @param color       the ARGB color value
     * @param displayMode the font display mode
     */
    private static void drawTextInternal(Font font, MultiBufferSource buffers,
                                         PoseStack poseStack, String text, float x, float y, int color,
                                         Font.DisplayMode displayMode) {
        poseStack.pushPose();
        poseStack.translate(0, 0, CONTENT_Z);
        font.drawInBatch(text, x, y, color, false,
                poseStack.last().pose(), buffers,
                displayMode, 0, GooSubmitter.fullbrightLight());
        poseStack.popPose();
    }

    /**
     * Applies billboard rotation so a panel faces the camera.
     * Yaw faces the camera; pitch tilts to match camera look angle, scaled by pitchFactor.
     *
     * @param poseStack   the pose stack for rendering
     * @param camera      the render camera
     * @param pitchFactor the pitch animation factor [0, 1]
     */
    public static void applyBillboardRotation(PoseStack poseStack, Camera camera,
                                              float pitchFactor) {
        float yaw = (float) Math.toRadians(-camera.yRot() + YAW_OFFSET);
        float pitch = (float) Math.toRadians(camera.xRot()) * pitchFactor;
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX(-pitch));
    }

    /**
     * Orients a panel flat against a block face, facing outward.
     * NORTH faces south (toward player looking north), SOUTH faces north, etc.
     * Only handles horizontal faces; UP/DOWN are handled by {@link #applyFlatRotation}.
     *
     * @param poseStack the pose stack for rendering
     * @param face      the block face direction
     */
    public static void applyFaceRotation(PoseStack poseStack, Direction face) {
        float yRot = switch (face) {
            case NORTH -> (float) Math.PI;        // 180 degrees
            case SOUTH -> 0f;
            case EAST -> (float) Math.PI / HALF;   // 90 degrees
            case WEST -> (float) -Math.PI / HALF;  // -90 degrees
            default -> 0f;
        };
        poseStack.mulPose(new Quaternionf().rotationY(yRot));
    }

    /**
     * Orients a panel to lie flat on the Y plane, billboarding yaw only.
     * The panel folds flat (X-rot 90) so the player looks down at it.
     *
     * @param poseStack the pose stack for rendering
     * @param camera    the render camera
     */
    public static void applyFlatRotation(PoseStack poseStack, Camera camera) {
        float yaw = (float) Math.toRadians(-camera.yRot() + YAW_OFFSET);
        poseStack.mulPose(new Quaternionf().rotationY(yaw));
        poseStack.mulPose(new Quaternionf().rotationX((float) Math.PI / HALF));
    }

    /**
     * Returns the horizontal direction whose outward normal is most anti-parallel to
     * the player's look vector - i.e. the face most directly visible to the player.
     *
     * @param look the player look direction vector
     * @return the horizontal direction most directly facing the player
     */
    public static Direction bestPerpendicularFace(Vec3 look) {
        double ax = Math.abs(look.x);
        double az = Math.abs(look.z);
        if (ax > az) {
            return look.x > 0 ? Direction.WEST : Direction.EAST;
        }
        return look.z > 0 ? Direction.NORTH : Direction.SOUTH;
    }

    /**
     * Computes frame delta-time in seconds from System.nanoTime().
     * The caller provides a single-element array that persists across frames.
     * Clamps to 0.1s to handle first-frame and lag spikes.
     *
     * @param lastFrameNanos single-element array storing previous frame time
     * @return delta time in seconds, clamped to {@link #MAX_DT}
     */
    public static float computeDeltaTime(long[] lastFrameNanos) {
        long now = System.nanoTime();
        float dt = (lastFrameNanos[0] == 0) ? DEFAULT_DT : (now - lastFrameNanos[0]) / NANOS_PER_SECOND;
        lastFrameNanos[0] = now;
        return Math.min(dt, MAX_DT);
    }
}
