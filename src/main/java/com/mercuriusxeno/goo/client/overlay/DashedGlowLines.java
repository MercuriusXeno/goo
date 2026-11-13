package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.VertexColors;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a sampled polyline as a glowing line: an undashed core and dashed halo
 * passes around it whose dashes scroll along the line, each pass wider and
 * dimmer than the last.
 */
final class DashedGlowLines {
    /** Dash segment length in world units. */
    private static final float DASH_ON = 0.5f;
    /** Gap length between dashes in world units. */
    private static final float DASH_OFF = 0.5f;
    /** Full dash cycle length (segment + gap = 1 meter). */
    private static final float DASH_CYCLE = DASH_ON + DASH_OFF;
    /** Scroll speed in world units per second, so dashes flow along the line. */
    private static final float SCROLL_SPEED = 1.5f;
    /** Maximum alpha channel value. */
    private static final int MAX_ALPHA = 255;
    /** Half segment midpoint for dash calculations. */
    private static final float DASH_MID = 0.5f;
    /** Width of the fade zone at each dash edge in world units. */
    private static final float DASH_FADE = 0.12f;
    /** Perspective scaling factor: how much to expand dashes per block of distance. */
    private static final float PERSPECTIVE_FACTOR = 0.04f;
    /** Ticks-per-second divisor for converting game time to seconds. */
    private static final float TICKS_PER_SECOND = 20.0f;

    private DashedGlowLines() {
    }

    /**
     * One line's bloom: an undashed core pass and the dashed halo passes
     * around it, each wider and dimmer than the last.
     *
     * @param passes     the pass count, the core plus its halos
     * @param coreAlpha  the core pass's alpha of 255
     * @param widthStep  the width added per halo pass, as a multiple of the base width
     * @param alphaDecay the alpha kept per halo pass
     */
    record GlowPalette(int passes, int coreAlpha, float widthStep, float alphaDecay) {
    }

    /**
     * Draws each line with the palette's bloom, its dashes at this frame's scroll.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param lines        each line's sampled points
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     * @param palette      the lines' bloom
     */
    static void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
                       Iterable<Vec3[]> lines, int rgb, float partialTick, GlowPalette palette) {
        float dashOffset = dashOffsetAt(frameSeconds(partialTick));
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        for (Vec3[] line : lines) {
            emitDashedGlow(poseStack, bufferSource, camera, line, rgb, dashOffset, width, palette);
        }
    }

    /**
     * The dash scroll offset at a frame clock, growing so the dashes flow
     * along the line.
     *
     * @param seconds the frame clock in seconds
     * @return dash offset in world units
     */
    static float dashOffsetAt(double seconds) {
        return (float) seconds * SCROLL_SPEED;
    }

    /**
     * The frame clock: game time plus partial tick, in seconds.
     *
     * @param partialTick the partial tick for the frame
     * @return seconds of game time at this frame
     */
    private static double frameSeconds(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level.getGameTime() + partialTick) / TICKS_PER_SECOND;
    }

    /**
     * Emits multi-pass glowing dashed lines for one sampled polyline.
     *
     * @param poseStack    the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera       the render camera
     * @param points       the sampled polyline points
     * @param rgb          the RGB color value
     * @param dashOffset   the dash scroll offset
     * @param baseWidth    the base line width
     * @param palette      the line's bloom
     */
    private static void emitDashedGlow(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
                                       Camera camera, Vec3[] points, int rgb, float dashOffset, float baseWidth,
                                       GlowPalette palette) {
        Vec3 cam = camera.position();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        for (int pass = palette.passes() - 1; pass >= 0; pass--) {
            float width = baseWidth * (1.0f + pass * palette.widthStep());
            int color = computeGlowPassColor(rgb, pass, palette);
            if (pass == 0) {
                ctx.emitPolyline(cam, points, color, width);
            } else {
                emitDashedPass(ctx, cam, points, dashOffset, color, width);
            }
        }
        bufferSource.endLastBatch();
    }

    /**
     * Computes the ARGB color for a bloom pass with exponential alpha decay.
     *
     * @param rgb     the base RGB color
     * @param pass    the bloom pass index (0 = core, higher = dimmer)
     * @param palette the line's bloom
     * @return the ARGB color with decayed alpha
     */
    static int computeGlowPassColor(int rgb, int pass, GlowPalette palette) {
        float alphaScale = (float) Math.pow(palette.alphaDecay(), pass);
        int alpha = Mth.clamp((int) (palette.coreAlpha() * alphaScale), 0, MAX_ALPHA);
        return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Emits one pass of dashed line segments along the polyline.
     *
     * @param ctx        the line render context
     * @param cam        the camera position
     * @param points     the sampled polyline points
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color value
     * @param width      the line width
     */
    private static void emitDashedPass(LineContext ctx, Vec3 cam, Vec3[] points, float dashOffset, int color,
                                       float width) {
        float arcLen = 0f;
        for (int i = 0; i < points.length - 1; i++) {
            arcLen = emitDashSegment(ctx, cam, points[i], points[i + 1], arcLen, dashOffset, color, width);
        }
    }

    /**
     * Emits a single dash segment if it falls in the "on" phase of the pattern.
     *
     * @param ctx        the line render context
     * @param cam        camera position
     * @param a          segment start
     * @param b          segment end
     * @param arcLen     accumulated length before this segment
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color
     * @param width      the line width
     * @return the updated accumulated length
     */
    private static float emitDashSegment(LineContext ctx, Vec3 cam, Vec3 a, Vec3 b, float arcLen,
                                         float dashOffset, int color, float width) {
        float segLen = (float) a.distanceTo(b);
        Vec3 mid = a.add(b).scale(DASH_MID);
        float perspScale = 1f + (float) cam.distanceTo(mid) * PERSPECTIVE_FACTOR;
        float alpha = dashAlpha(arcLen + segLen * DASH_MID, dashOffset, perspScale);
        if (alpha > 0f) {
            ctx.emitEdge(
                    (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                    (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z),
                    VertexColors.scaleAlpha(color, alpha), width);
        }
        return arcLen + segLen;
    }

    /**
     * Returns an alpha multiplier [0..1] for the dash at the given length:
     * full in the dash interior, fading to zero at its edges. The dash cycle
     * is scaled by {@code perspScale} so distant segments appear longer,
     * compensating for perspective compression.
     *
     * @param midArcLen  the length along the line at the segment midpoint
     * @param dashOffset the dash scroll offset
     * @param perspScale perspective scale factor (1.0 = no scaling)
     * @return 0 in the gap, 1 in the dash interior, smooth fade at edges
     */
    private static float dashAlpha(float midArcLen, float dashOffset, float perspScale) {
        float scaledCycle = DASH_CYCLE * perspScale;
        float scaledOn = DASH_ON * perspScale;
        float phase = (midArcLen - dashOffset * perspScale) % scaledCycle;
        if (phase < 0) {
            phase += scaledCycle;
        }
        if (phase >= scaledOn) {
            return 0f;
        }
        float edgeDist = Math.min(phase, scaledOn - phase);
        return Math.min(edgeDist / (DASH_FADE * perspScale), 1f);
    }
}
