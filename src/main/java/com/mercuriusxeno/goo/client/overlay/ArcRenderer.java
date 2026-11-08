package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.VertexColors;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.throwing.GateArc;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Renders a dashed arc polyline from the glove hand to the target, previewing
 * the throw trajectory, and the touch reach ring, each with its own bloom.
 */
public final class ArcRenderer {
    /** Dash segment length in world units. */
    private static final float DASH_ON = 0.5f;
    /** Gap length between dashes in world units. */
    private static final float DASH_OFF = 0.5f;
    /** Full dash cycle length (segment + gap = 1 meter). */
    private static final float DASH_CYCLE = DASH_ON + DASH_OFF;
    /** Scroll speed in world units per second - dashes flow toward the target. */
    private static final float SCROLL_SPEED = 1.5f;
    /** Distance between polyline sample points on the arc. */
    private static final float SAMPLE_SPACING = 0.05f;

    /**
     * The aim line's bloom: a faint core and one narrow dashed halo, a quiet
     * guide a thrown dart reads over, the halo kept since its dashes carry the
     * line's scroll.
     * aim-line-draws-quieter-so-the-dart-reads
     */
    static final GlowPalette AIM_LINE_GLOW = new GlowPalette(2, 36, 0.75f, 0.6f);
    /**
     * The touch reach ring's bloom, which shows the reach around the player's
     * feet with no dart flying over it.
     * mob-ability-touches-at-reach
     */
    static final GlowPalette REACH_RING_GLOW = new GlowPalette(3, 80, 1.5f, 0.55f);
    /**
     * Knives' aim lines' bloom: fainter than the aim line, since a press
     * draws one per knife and they overlay near the hand.
     * knives-aims-a-spread-fan-not-a-blob-line
     */
    static final GlowPalette KNIFE_LINE_GLOW = new GlowPalette(2, 20, 0.75f, 0.6f);

    /** Minimum arc segment count. */
    private static final int MIN_ARC_SEGMENTS = 8;
    /** Maximum arc segment count. */
    private static final int MAX_ARC_SEGMENTS = 1280;
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

    /** Segments of the touch reach ring. */
    private static final int RING_SEGMENTS = 48;

    private ArcRenderer() {}

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
     * Renders a glowing animated dashed arc from the glove hand to the target,
     * previewing the throw trajectory.
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param end          the target endpoint position
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     * @param grannyWeight blend of the peak rules: 0 the plain peak, 1 the lob peak
     * @param straightLine if true, peak is zero (straight line, no arc)
     */
    static void renderTargetArc(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 end,
            int rgb, float partialTick, double grannyWeight, boolean straightLine) {
        Vec3 start = GloveAim.handPosition(camera);
        Vec3[] points = sampleArcPoints(start, end, grannyWeight, straightLine);
        float dashOffset = computeDashOffset(partialTick);
        Minecraft mc = Minecraft.getInstance();
        emitDashedGlow(poseStack, bufferSource, camera, points, rgb, dashOffset,
                mc.getWindow().getAppropriateLineWidth(), AIM_LINE_GLOW);
    }

    /**
     * Renders one leg of an aim line that passes a gate: the near leg, from
     * the hand to the gate's face, in the main frame, or the far leg, from the
     * partner to the target, in the view through the gate, each drawn from the
     * camera of the pass it draws in. One curve over both legs' summed length
     * shapes them, so the far leg carries on the near one through the gate.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the camera of the pass drawing
     * @param hand         the hand, where the line starts
     * @param hop          the gate the line passes
     * @param end          the target endpoint position
     * @param far          true for the far leg, false for the near leg
     * @param style        the line's color and shape
     */
    static void renderGateLeg(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Camera camera,
            Vec3 hand, GateArc.Hop hop, Vec3 end, boolean far, LineStyle style) {
        double length = hand.distanceTo(hop.facePoint()) + hop.partnerPoint().distanceTo(end);
        double peak = style.straightLine() ? 0 : Mth.lerp(style.grannyWeight(), ThrowArc.basePeak(length),
                ThrowArc.lobPeak(hop.partnerPoint(), end));
        int segments = Mth.clamp((int) (length / SAMPLE_SPACING), MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS);
        List<Vec3[]> legs = GateArc.legs(hand, hop, end, peak, segments);
        emitDashedGlow(poseStack, bufferSource, camera, legs.get(far ? 1 : 0), style.rgb(),
                computeDashOffset(style.partialTick()), Minecraft.getInstance().getWindow().getAppropriateLineWidth(),
                AIM_LINE_GLOW);
    }

    /**
     * How an aim line draws.
     *
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     * @param grannyWeight blend of the peak rules: 0 the plain peak, 1 the lob peak
     * @param straightLine true for a beam, drawn straight
     */
    record LineStyle(int rgb, float partialTick, double grannyWeight, boolean straightLine) {
    }

    /**
     * Renders the glowing dashed ring a mob ability touches within, level
     * around the player's feet.
     * decision mob-ability-touches-at-reach
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param center       the player's feet
     * @param radius       the touch's reach in blocks
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     */
    static void renderReachRing(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3 center, double radius, int rgb, float partialTick) {
        Vec3[] points = ringPoints(center, radius, RING_SEGMENTS);
        emitDashedGlow(poseStack, bufferSource, camera, points, rgb,
                computeDashOffset(partialTick), Minecraft.getInstance().getWindow().getAppropriateLineWidth(),
                REACH_RING_GLOW);
    }

    /**
     * Renders Knives' aim: a faint dashed line along each knife's flight.
     * knives-aims-a-spread-fan-not-a-blob-line
     *
     * @param poseStack    the current pose stack
     * @param bufferSource the buffer source for render output
     * @param camera       the active camera
     * @param lines        each knife's flight, from the hand
     * @param rgb          the RGB color for tinting
     * @param partialTick  the partial tick for animation
     */
    static void renderKnifeLines(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, List<Vec3[]> lines, int rgb, float partialTick) {
        float dashOffset = computeDashOffset(partialTick);
        float width = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        for (Vec3[] line : lines) {
            emitDashedGlow(poseStack, bufferSource, camera, line, rgb, dashOffset, width, KNIFE_LINE_GLOW);
        }
    }

    /**
     * Samples a level circle as a closed polyline: the last point repeats the first.
     *
     * @param center   the circle's center
     * @param radius   the circle's radius in blocks
     * @param segments the number of segments
     * @return the sampled points, segments + 1 of them
     */
    static Vec3[] ringPoints(Vec3 center, double radius, int segments) {
        Vec3[] points = new Vec3[segments + 1];
        for (int i = 0; i <= segments; i++) {
            double angle = Math.TAU * i / segments;
            points[i] = center.add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
        }
        return points;
    }

    /**
     * Samples the throw arc polyline from start to end.
     *
     * @param start     arc origin (hand position)
     * @param end       arc destination (target center)
     * @param grannyWeight blend of the peak rules: 0 the plain peak, 1 the lob peak
     * @param straightLine true for zero peak (straight line)
     * @return sampled polyline points
     */
    private static Vec3[] sampleArcPoints(Vec3 start, Vec3 end,
            double grannyWeight, boolean straightLine) {
        double distance = start.distanceTo(end);
        double peak = straightLine ? 0 : computeArcPeak(start, end, grannyWeight);
        int segments = Mth.clamp(
                (int) (distance / SAMPLE_SPACING),
                MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS);
        return ThrowArc.sampleArc(start, end, peak, segments);
    }

    /**
     * Blends the plain and lob peak heights by weight, the target's own 0 or 1
     * now that the line draws on the target the same frame it changes
     * (decision aim-line-snaps-with-the-tile-highlight).
     *
     * @param start        arc origin (hand position)
     * @param end          arc destination
     * @param grannyWeight 0 for the plain peak, 1 for the lob peak
     * @return the arc peak height
     */
    public static double computeArcPeak(Vec3 start, Vec3 end, double grannyWeight) {
        return Mth.lerp(grannyWeight, ThrowArc.basePeak(start.distanceTo(end)), ThrowArc.lobPeak(start, end));
    }

    /**
     * Computes the scrolling dash offset based on game time.
     *
     * @param partialTick the partial tick for smooth animation
     * @return dash offset in world units
     */
    private static float computeDashOffset(float partialTick) {
        return dashOffsetAt(frameSeconds(partialTick));
    }

    /**
     * The dash scroll offset at a frame clock, growing so the dashes flow
     * toward the target.
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
     * Emits multi-pass glowing dashed lines for the sampled arc polyline.
     *
     * @param poseStack the pose stack for rendering
     * @param bufferSource the buffer source for rendering
     * @param camera the render camera
     * @param points the sampled arc polyline points
     * @param rgb the RGB color value
     * @param dashOffset the dash scroll offset
     * @param baseWidth the base line width
     * @param palette the line's bloom
     */
    private static void emitDashedGlow(
            PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            Camera camera, Vec3[] points,
            int rgb, float dashOffset, float baseWidth, GlowPalette palette) {
        int segments = points.length - 1;
        Vec3 cam = camera.position();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(GooRenderTypes.LINES_GLOW));
        for (int pass = palette.passes() - 1; pass >= 0; pass--) {
            float width = baseWidth * (1.0f + pass * palette.widthStep());
            int color = computeGlowPassColor(rgb, pass, palette);
            if (pass == 0) {
                ctx.emitPolyline(cam, points, color, width);
            } else {
                emitDashedPass(ctx, cam, points, segments,
                        dashOffset, color, width);
            }
        }
        bufferSource.endLastBatch();
    }

    /**
     * Computes the ARGB color for a bloom pass with exponential alpha decay.
     *
     * @param rgb  the base RGB color
     * @param pass the bloom pass index (0 = core, higher = dimmer)
     * @param palette the line's bloom
     * @return the ARGB color with decayed alpha
     */
    static int computeGlowPassColor(int rgb, int pass, GlowPalette palette) {
        float alphaScale = (float) Math.pow(palette.alphaDecay(), pass);
        int alpha = Mth.clamp((int) (palette.coreAlpha() * alphaScale), 0, MAX_ALPHA);
        return ARGB.color(alpha, ARGB.red(rgb), ARGB.green(rgb), ARGB.blue(rgb));
    }

    /**
     * Emits one pass of dashed line segments for the arc polyline.
     *
     * @param ctx        the line render context
     * @param cam        the camera position
     * @param points     the sampled arc polyline points
     * @param segments   the number of arc segments
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color value
     * @param width      the line width
     */
    private static void emitDashedPass(
            LineContext ctx, Vec3 cam, Vec3[] points, int segments,
            float dashOffset, int color, float width) {
        float arcLen = 0f;
        for (int i = 0; i < segments; i++) {
            arcLen = emitDashSegment(ctx, cam,
                    points[i], points[i + 1],
                    arcLen, dashOffset, color, width);
        }
    }

    /**
     * Emits a single dash segment if it falls in the "on" phase of the pattern.
     *
     * @param ctx        the line render context
     * @param cam        camera position
     * @param a          segment start
     * @param b          segment end
     * @param arcLen     accumulated arc length before this segment
     * @param dashOffset the dash scroll offset
     * @param color      the ARGB color
     * @param width      the line width
     * @return the updated accumulated arc length
     */
    private static float emitDashSegment(
            LineContext ctx, Vec3 cam, Vec3 a, Vec3 b, float arcLen,
            float dashOffset, int color, float width) {
        float segLen = (float) a.distanceTo(b);
        Vec3 mid = a.add(b).scale(DASH_MID);
        float camDist = (float) cam.distanceTo(mid);
        float perspScale = 1f + camDist * PERSPECTIVE_FACTOR;
        float alpha = dashAlpha(arcLen + segLen * DASH_MID, dashOffset, perspScale);
        if (alpha > 0f) {
            int fadedColor = VertexColors.scaleAlpha(color, alpha);
            ctx.emitEdge(
                (float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z),
                (float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z),
                fadedColor, width);
        }
        return arcLen + segLen;
    }

    /**
     * Returns an alpha multiplier [0..1] for the dash at the given arc-length.
     * Full brightness in the dash interior, fading to zero at the edges.
     * The dash cycle is scaled by {@code perspScale} so distant segments
     * appear longer, compensating for perspective compression.
     *
     * @param midArcLen  the arc-length at the segment midpoint
     * @param dashOffset the dash scroll offset
     * @param perspScale perspective scale factor (1.0 = no scaling)
     * @return 0 in the gap, 1 in the dash interior, smooth fade at edges
     */
    private static float dashAlpha(float midArcLen, float dashOffset, float perspScale) {
        float scaledCycle = DASH_CYCLE * perspScale;
        float scaledOn = DASH_ON * perspScale;
        float phase = (midArcLen - dashOffset * perspScale) % scaledCycle;
        if (phase < 0) { phase += scaledCycle; }
        if (phase >= scaledOn) { return 0f; }
        float edgeDist = Math.min(phase, scaledOn - phase);
        float scaledFade = DASH_FADE * perspScale;
        return Math.min(edgeDist / scaledFade, 1f);
    }
}
