package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;

/**
 * Renders fluid stream columns: the vat's pulsing fill stream and the tap's
 * pour. Every stream tiles its fluid sprite down the column at the sprite's
 * native scale, flowing downward, through {@link #emitTiledColumn}
 * (decision diagnose-then-fix-stream-tiling).
 */
public final class GooStreamRenderer {
    /** Minimum stream half-width at 1 mB/tick (tiny trickle). */
    private static final float MIN_HW = 0.125f / 16f;

    /** Maximum stream half-width at 3700+ mB/tick (half canister width). */
    private static final float MAX_HW = 1.0f / 16f;

    /** Rate at which max width is reached, in mB/tick. */
    private static final float MAX_RATE = 3700f;

    /** Sin wave frequency for width pulsing (~2-second period). */
    private static final float PULSE_FREQUENCY = 0.3f;

    /** Sin wave amplitude (±10% width variation). */
    private static final float PULSE_AMPLITUDE = 0.1f;

    /** Alpha of the semi-transparent stream. */
    private static final int STREAM_ALPHA = 0xB0;

    /** Semi-transparent ARGB for the stream. */
    private static final int STREAM_COLOR = ARGB.color(STREAM_ALPHA, GooRenderUtil.OPAQUE_WHITE);

    /** Columns are measured against one sprite per block of height and width. */
    private static final float SPRITE_BLOCKS = 1f;

    /** Width of a column is twice its half-width. */
    private static final float WIDTH_PER_HALF_WIDTH = 2f;

    /** How fast a stream's texture runs down its column, in blocks per tick (2 blocks a second). */
    private static final float FLOW_BLOCKS_PER_TICK = 0.1f;

    private GooStreamRenderer() {}

    /**
     * Renders a pulsing goo stream from yTop down to yBottom.
     *
     * @param ctx           the render context
     * @param cx            stream center X in block coords
     * @param cz            stream center Z in block coords
     * @param yTop          top of stream (bottom of upper gasket)
     * @param yBottom       bottom of stream (current fluid surface level)
     * @param type          goo type for sprite/color
     * @param rate          transfer rate in mB/tick (controls width)
     * @param animationTime game time + partial tick for sin wave
     */
    public static void renderStream(RenderContext ctx,
                                    float cx, float cz, float yTop, float yBottom,
                                    ResourceKey<GooTypeDefinition> type, float rate, float animationTime) {
        renderStream(ctx, new StreamColumn(cx, cz, yTop, yBottom), GooRenderUtil.lookupFluidSprite(type),
                STREAM_COLOR, rate, animationTime);
    }

    /**
     * Renders a pulsing vanilla fluid stream from yTop down to yBottom.
     *
     * @param ctx           the render context
     * @param cx            stream center X in block coords
     * @param cz            stream center Z in block coords
     * @param yTop          top of stream
     * @param yBottom       bottom of stream
     * @param fluid         the vanilla fluid
     * @param rate          transfer rate in mB/tick
     * @param animationTime game time + partial tick for sin wave
     */
    public static void renderStream(RenderContext ctx,
            float cx, float cz, float yTop, float yBottom,
            Fluid fluid, float rate, float animationTime) {
        renderStream(ctx, new StreamColumn(cx, cz, yTop, yBottom), GooSubmitter.fluidSprite(fluid),
                ARGB.color(STREAM_ALPHA, GooSubmitter.fluidTint(fluid)), rate, animationTime);
    }

    /**
     * Renders a pulsing stream of a resolved sprite and color, its width set
     * by the transfer rate, tiled down the column.
     *
     * @param ctx           the render context
     * @param column        where the stream runs
     * @param sprite        the fluid sprite
     * @param color         the ARGB color
     * @param rate          transfer rate in mB/tick
     * @param animationTime game time + partial tick for sin wave
     */
    static void renderStream(RenderContext ctx, StreamColumn column, TextureAtlasSprite sprite, int color,
                             float rate, float animationTime) {
        emitTiledColumn(ctx, column, computeHalfWidth(rate, animationTime), sprite, color, flowPhase(animationTime));
    }

    /**
     * How far a stream's texture has run down its column, in blocks, for the
     * flow to loop downward over time.
     *
     * @param animationTime game time plus partial tick
     * @return the flow phase in blocks
     */
    public static float flowPhase(float animationTime) {
        return animationTime * FLOW_BLOCKS_PER_TICK;
    }

    /**
     * Emits a column's four sides with the sprite tiled at its native scale,
     * scrolled down the column by the flow phase so the goo reads as flowing:
     * the texture at a distance below the top reads the sprite at that
     * distance less the phase, wrapped, so the column splits wherever the
     * sprite wraps into segments no taller than a block, each mapped to its
     * own V span; U covers the column's width as a fraction of the sprite. No
     * emitted UV leaves the sprite's atlas bounds (decision diagnose-then-fix-stream-tiling).
     *
     * @param ctx       the render context
     * @param column    where the column runs
     * @param halfWidth the column's half-width in blocks
     * @param sprite    the fluid sprite
     * @param color     the ARGB color
     * @param flowPhase how far the texture has run down the column, in blocks
     */
    public static void emitTiledColumn(RenderContext ctx, StreamColumn column, float halfWidth,
                                       TextureAtlasSprite sprite, int color, float flowPhase) {
        if (column.yTop() <= column.yBottom()) {
            return;
        }
        float widthFraction = Math.min(SPRITE_BLOCKS, halfWidth * WIDTH_PER_HALF_WIDTH);
        float vStart = wrap(-flowPhase);
        float segmentTop = column.yTop();
        while (segmentTop > column.yBottom()) {
            float segmentBottom = Math.max(column.yBottom(), segmentTop - (SPRITE_BLOCKS - vStart));
            float vEnd = vStart + (segmentTop - segmentBottom);
            CuboidBounds segment = new CuboidBounds(column.cx() - halfWidth, column.cx() + halfWidth,
                    column.cz() - halfWidth, column.cz() + halfWidth, segmentBottom, segmentTop);
            ctx.emitSides(color, segment, GooSubmitter.spriteSubRect(sprite, 0f, vStart, widthFraction, vEnd));
            segmentTop = segmentBottom;
            vStart = 0f;
        }
    }

    /**
     * @param blocks a length in blocks
     * @return its fraction of one sprite, in [0, 1)
     */
    private static float wrap(float blocks) {
        return blocks - (float) Math.floor(blocks);
    }

    /**
     * Computes the pulsing half-width from transfer rate and animation time.
     * @param rate the transfer rate in mB/tick
     * @param animationTime the game time plus partial tick for sin wave
     * @return the pulsing half-width in block coords
     */
    private static float computeHalfWidth(float rate, float animationTime) {
        float t = Mth.clamp(rate / MAX_RATE, 0f, 1f);
        float baseHW = Mth.lerp(t, MIN_HW, MAX_HW);
        float pulse = 1.0f + PULSE_AMPLITUDE * Mth.sin(animationTime * PULSE_FREQUENCY);
        return baseHW * pulse;
    }

    /**
     * Where a stream column runs, in block-local coordinates.
     *
     * @param cx      center X
     * @param cz      center Z
     * @param yTop    top of the column
     * @param yBottom bottom of the column
     */
    public record StreamColumn(float cx, float cz, float yTop, float yBottom) {
    }
}
