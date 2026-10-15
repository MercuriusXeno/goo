package com.mercuriusxeno.goo.client.model;

import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Shared fluid-face emission for any container whose fluid volume is
 * represented as a {@link CuboidBounds}. Emits the translucent liquid top
 * quad plus the four vertical side quads, with UVs scaled proportionally
 * to the cuboid's XYZ extent so the sprite is not squished. The sprite
 * and tint come from the caller, which takes them from the submitter.
 *
 * <p>The fluid cuboid's Y range is expected to already encode the fill
 * level - callers construct it as {@code (yFloor, yFloor + fillHeight)}.
 * This helper reads the Y span from the cuboid directly, so no separate
 * {@code fill} parameter is needed.
 */
public final class FluidFaceEmitter {

    private FluidFaceEmitter() {}

    /**
     * Emits the top fluid surface quad and the four side quads for the
     * given cuboid on the given sprite and tint.
     *
     * @param ctx    the render context
     * @param b      the fluid cuboid - its Y range must already encode the fill height
     * @param sprite the fluid texture sprite
     * @param tint   the ARGB tint color
     */
    public static void emitFluidFaces(RenderContext ctx, CuboidBounds b,
            TextureAtlasSprite sprite, int tint) {
        emitFaces(ctx, b, tint, (spanU, spanV) -> GooSubmitter.spriteSubRect(sprite, 0f, 0f, spanU, spanV));
    }

    /**
     * Emits the same faces with sprite-relative UVs, 0 to 1 spanning a whole
     * sprite, in the context color, for a one-pass mingled surface whose
     * shader maps them into each type's sprite (decision one-pass-blends-every-type).
     *
     * @param ctx the render context
     * @param b   the fluid cuboid - its Y range must already encode the fill height
     */
    public static void emitSpriteRelativeFaces(RenderContext ctx, CuboidBounds b) {
        emitFaces(ctx, b, ctx.color(), (spanU, spanV) -> new GooRenderUtil.UvRect(0f, 0f, spanU, spanV));
    }

    /** Maps a face's extent in sprite widths to the UV rect it draws on. */
    @FunctionalInterface
    private interface FaceUv {
        GooRenderUtil.UvRect spanning(float spanU, float spanV);
    }

    private static void emitFaces(RenderContext ctx, CuboidBounds b, int tint, FaceUv faceUv) {
        float spanX = b.x1() - b.x0();
        float spanY = b.yTop() - b.yBot();
        float spanZ = b.z1() - b.z0();
        ctx.liquidSurface(tint, b, faceUv.spanning(spanX, spanZ));
        emitAllSideFaces(ctx, b, faceUv.spanning(spanX, spanY), faceUv.spanning(spanZ, spanY), tint);
    }

    /**
     * Emits all four cardinal side faces with the given tint color.
     *
     * @param ctx  the render context
     * @param b    the cuboid bounds
     * @param xUv  UV rect for the north/south faces
     * @param zUv  UV rect for the west/east faces
     * @param tint the ARGB tint color
     */
    private static void emitAllSideFaces(RenderContext ctx, CuboidBounds b,
                                         GooRenderUtil.UvRect xUv, GooRenderUtil.UvRect zUv,
                                         int tint) {
        ctx.emitFace(tint, b, xUv, Direction.NORTH);
        ctx.emitFace(tint, b, xUv, Direction.SOUTH);
        ctx.emitFace(tint, b, zUv, Direction.WEST);
        ctx.emitFace(tint, b, zUv, Direction.EAST);
    }
}
