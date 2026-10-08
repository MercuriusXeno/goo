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
import net.minecraft.world.phys.Vec3;

/**
 * A block's goo streaming into the soup: strands of its goo that spiral about
 * the line from the block to the ball in a snaking vortex, tightening as they
 * near the ball. Through the first half of the siphon the strands reach out
 * from the block to the ball; through the second their tails leave the block
 * and follow them in.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SiphonStream {

    /** Strands spiraling in together, evenly about the line. */
    static final int STRANDS = 2;
    /** Turns each strand makes about the line from the block to the ball. */
    static final double TURNS = 1.5;
    /** How far a strand swings from the line at the block, in blocks. */
    static final double SWING = 0.35;
    /** Turns the vortex spins each tick. */
    static final double SPIN = 0.08;
    /** A strand's width at the block, in blocks. */
    static final double WIDTH = 0.12;
    /** How much thinner a strand runs at the ball. */
    static final double TAPER = 0.6;
    private static final int SEGMENTS = 24;
    private static final int SIDES = 4;
    private static final int OPAQUE = 0xFF;
    private static final double TWO_PI = 2 * Math.PI;
    private static final double HALF = 0.5;
    private static final double NEAR_VERTICAL = 0.99;
    private static final double SMOOTH_BASE = 3;
    private static final double SMOOTH_SLOPE = 2;
    /** How much faster than the siphon the strands' ends travel, so each crosses in half of it. */
    private static final double SPAN_RATE = 2;

    private SiphonStream() {
    }

    /**
     * The stretch of the line the strands span at a point of the siphon.
     *
     * @param tail where the strands' tails are, 0 at the block
     * @param head where their heads are, 1 at the ball
     */
    record Span(double tail, double head) {
    }

    /**
     * @param progress how far the siphon has run, 0 to 1
     * @return the stretch the strands span: reaching the ball by half way, leaving the block after
     */
    static Span spanAt(double progress) {
        return new Span(Math.clamp(SPAN_RATE * progress - 1, 0, 1), Math.clamp(SPAN_RATE * progress, 0, 1));
    }

    /**
     * Submits one block's streams.
     *
     * @param poseStack the level's pose stack, camera relative
     * @param collector the node collector
     * @param from      the block's middle, camera relative
     * @param to        the ball's middle, camera relative
     * @param progress  how far the siphon has run, 0 to 1
     * @param ticks     the game time including the partial tick
     * @param goo       the block's goo
     */
    public static void submit(PoseStack poseStack, SubmitNodeCollector collector, Vec3 from, Vec3 to,
                              float progress, double ticks, GooContents goo) {
        Span span = spanAt(progress);
        if (span.head() <= span.tail() || from.distanceToSqr(to) == 0) {
            return;
        }
        RenderType surface = GooRenderTypes.gooFluidSurface(
                Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).location());
        Line line = Line.between(from, to);
        for (TypeBand band : TypeBands.over(goo)) {
            GooRenderUtil.UvRect sprite = GooSubmitter.spriteUv(GooRenderUtil.lookupFluidSprite(band.type()));
            int color = ARGB.color(OPAQUE, GooSubmitter.fluidTint(band.type()));
            collector.submitCustomGeometry(poseStack, surface, (pose, consumer) -> {
                RenderContext ctx = RenderContext.banded(pose, consumer, color, band);
                for (int strand = 0; strand < STRANDS; strand++) {
                    emitStrand(ctx, sprite, line, span, ticks, (double) strand / STRANDS);
                }
            });
        }
    }

    /**
     * The line a stream runs along and two directions square to it.
     *
     * @param from  the block's middle
     * @param to    the ball's middle
     * @param side  a unit direction square to the line
     * @param up    a unit direction square to the line and to side
     */
    private record Line(Vec3 from, Vec3 to, Vec3 side, Vec3 up) {

        static Line between(Vec3 from, Vec3 to) {
            Vec3 along = to.subtract(from).normalize();
            Vec3 side = along.cross(Math.abs(along.y) < NEAR_VERTICAL ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0))
                    .normalize();
            return new Line(from, to, side, side.cross(along));
        }
    }

    /**
     * A point of a strand: along the line on a smooth ease, swung about it on
     * the vortex, the swing closing to nothing at the ball.
     *
     * @param from  the block's middle
     * @param to    the ball's middle
     * @param side  a unit direction square to the line
     * @param up    a unit direction square to the line and to side
     * @param t     how far along, 0 at the block to 1 at the ball
     * @param ticks the game time including the partial tick
     * @param phase the strand's place about the line, in turns
     * @return the point
     */
    static Vec3 strandAt(Vec3 from, Vec3 to, Vec3 side, Vec3 up, double t, double ticks, double phase) {
        double eased = t * t * (SMOOTH_BASE - SMOOTH_SLOPE * t);
        double angle = TWO_PI * (TURNS * t - SPIN * ticks + phase);
        double swing = SWING * (1 - t);
        return from.add(to.subtract(from).scale(eased))
                .add(side.scale(Math.cos(angle) * swing)).add(up.scale(Math.sin(angle) * swing));
    }

    private static void emitStrand(RenderContext ctx, GooRenderUtil.UvRect sprite, Line line, Span span,
                                   double ticks, double phase) {
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double t0 = span.tail() + (span.head() - span.tail()) * segment / SEGMENTS;
            double t1 = span.tail() + (span.head() - span.tail()) * (segment + 1) / SEGMENTS;
            Vec3 p0 = strandAt(line.from(), line.to(), line.side(), line.up(), t0, ticks, phase);
            Vec3 p1 = strandAt(line.from(), line.to(), line.side(), line.up(), t1, ticks, phase);
            double w0 = WIDTH * (1 - TAPER * t0) * HALF;
            double w1 = WIDTH * (1 - TAPER * t1) * HALF;
            float v0 = sprite.v0() + (sprite.v1() - sprite.v0()) * segment / SEGMENTS;
            float v1 = sprite.v0() + (sprite.v1() - sprite.v0()) * (segment + 1) / SEGMENTS;
            for (int face = 0; face < SIDES; face++) {
                Vec3 a = around(line, face, w0);
                Vec3 b = around(line, face + 1, w0);
                Vec3 c = around(line, face + 1, w1);
                Vec3 d = around(line, face, w1);
                Vec3 normal = around(line, face, 1).add(around(line, face + 1, 1)).normalize();
                vertex(ctx, p0.add(a), sprite.u0(), v0, normal);
                vertex(ctx, p0.add(b), sprite.u1(), v0, normal);
                vertex(ctx, p1.add(c), sprite.u1(), v1, normal);
                vertex(ctx, p1.add(d), sprite.u0(), v1, normal);
            }
        }
    }

    private static Vec3 around(Line line, int corner, double reach) {
        double angle = TWO_PI * corner / SIDES;
        return line.side().scale(Math.cos(angle) * reach).add(line.up().scale(Math.sin(angle) * reach));
    }

    private static void vertex(RenderContext ctx, Vec3 at, float u, float v, Vec3 normal) {
        ctx.vertex((float) at.x, (float) at.y, (float) at.z, u, v, (float) normal.x, (float) normal.y,
                (float) normal.z);
    }
}
