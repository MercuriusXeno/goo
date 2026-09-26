package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

/**
 * Fluid geometry and stack-fill computation helpers for {@link VatBlockEntityRenderer}.
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class VatFluidRenderer {

    /** Interior wall insets in block coords (1/16 from each side). */
    private static final float MIN_X = 1f / 16f;
    private static final float MAX_X = 15f / 16f;
    private static final float MIN_Z = 1f / 16f;
    private static final float MAX_Z = 15f / 16f;

    /** Inset from walls to prevent z-fighting (1px past inner wall faces). */
    private static final float INSET = 1.0f / 16f;

    /** Vertical nudge above floor to prevent z-fighting at low fill. */
    private static final float Y_EPSILON = 0.001f;

    /** Epsilon threshold for full-submersion check. */
    private static final float SUBMERSION_EPSILON = 0.0001f;

    /** Number of intermediate vats excluded from total interior. */
    private static final int MIDDLE_EXCLUDED = 2;

    private VatFluidRenderer() {
    }

    /**
     * Renders this vat's portion of the unified fluid column once per type
     * layer, top, underside and side faces alike, each layer lifted outward
     * of the one below, so the column reads mingled through the glass
     * (decision noise-mingled-type-textures).
     *
     * @param submitter submits one band's surface on that band type's sprite
     * @param state     the block state
     */
    static void renderMingledFluid(BandedSurfaceSubmitter submitter, VatRenderState state) {
        for (TypeBand band : state.typeBands) {
            submitter.submit(band, (ctx, sprite) -> renderFluid(ctx, sprite, state, band.lift()));
        }
    }

    /**
     * Renders this vat's portion of the unified fluid column on the sprite
     * the submitter resolved. Computes local floor/ceiling from stack
     * position, then determines how much of this vat's interior is submerged.
     *
     * @param ctx    the render context
     * @param sprite the fluid sprite of the band's goo type
     * @param state  the block state
     * @param lift   the distance the geometry sits outward of layer 0
     */
    static void renderFluid(RenderContext ctx, TextureAtlasSprite sprite, VatRenderState state, float lift) {
        float localFloor = state.vatBelow ? 0f : VatBlockEntityRenderer.BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : VatBlockEntityRenderer.CAP_CEILING;
        float localFill = computeLocalFill(state, localFloor, localCeiling);
        if (localFill <= 0f) { return; }

        CuboidBounds b = computeVatCuboidBounds(state, localFloor, localFill);
        renderVatTopFaces(ctx, b, sprite, localCeiling - localFloor, localFill, state.rippleAmplitude, lift);
        renderVatSideFaces(ctx, b, sprite, localCeiling - localFloor, lift);
    }

    /**
     * Grows the bounds outward by the lift on every side and the top.
     *
     * @param b    the fluid cuboid bounds
     * @param lift the distance outward
     * @return the grown bounds
     */
    private static CuboidBounds liftOutward(CuboidBounds b, float lift) {
        return new CuboidBounds(b.x0() - lift, b.x1() + lift, b.z0() - lift, b.z1() + lift,
            b.yBot(), b.yTop() + lift);
    }

    /**
     * Computes inset XZ bounds and Y range for the vat fluid column.
     * @param state      the vat render state with stack topology
     * @param localFloor the Y offset of this vat's floor within the stack
     * @param localFill  the fill height in block units
     * @return the fluid cuboid bounds
     */
    private static CuboidBounds computeVatCuboidBounds(VatRenderState state,
            float localFloor, float localFill) {
        float x0 = MIN_X + INSET;
        float x1 = MAX_X - INSET;
        float z0 = MIN_Z + INSET;
        float z1 = MAX_Z - INSET;
        float yBot = localFloor + (state.vatBelow ? 0f : Y_EPSILON);
        float yTop = localFloor + localFill;
        return new CuboidBounds(x0, x1, z0, z1, yBot, yTop);
    }

    /**
     * Renders the rippling top/bottom grids at the air-liquid interface if not fully submerged.
     *
     * @param ctx         the render context
     * @param b           the precomputed fluid cuboid bounds
     * @param sprite      the fluid texture atlas sprite
     * @param localHeight the total vat height in block units
     * @param localFill   the fill height in block units
     * @param amplitude   the ripple amplitude the interior vertices carry
     * @param lift        the distance the top sits above, and the underside below, layer 0's
     */
    private static void renderVatTopFaces(RenderContext ctx, CuboidBounds b,
                                          TextureAtlasSprite sprite, float localHeight, float localFill,
                                          float amplitude, float lift) {
        boolean isFullySubmerged = localFill >= localHeight - SUBMERSION_EPSILON;
        if (isFullySubmerged) { return; }
        GooRenderUtil.UvRect uv = GooSubmitter.spriteUv(sprite);
        CuboidBounds lifted = liftOutward(b, lift);
        ctx.liquidSurfaceGrid(lifted, uv, amplitude);
        ctx.liquidSurfaceGridDown(lifted.withY(b.yBot(), b.yTop() - lift), uv, amplitude);
    }

    /**
     * Renders the four side faces with UV pinned at the bottom.
     *
     * @param ctx         the render context
     * @param b           the precomputed fluid cuboid bounds
     * @param sprite      the fluid texture atlas sprite
     * @param localHeight the total vat height in block units
     * @param lift        the distance the faces sit outward of layer 0's
     */
    private static void renderVatSideFaces(RenderContext ctx, CuboidBounds b,
                                           TextureAtlasSprite sprite, float localHeight, float lift) {
        GooRenderUtil.UvRect uv = computeSideUv(sprite, b, localHeight);
        CuboidBounds lifted = liftOutward(b, lift);
        ctx.emitFace(lifted, uv, Direction.NORTH);
        ctx.emitFace(lifted, uv, Direction.SOUTH);
        ctx.emitFace(lifted, uv, Direction.WEST);
        ctx.emitFace(lifted, uv, Direction.EAST);
    }

    /**
     * Computes UV rect for side faces with the V range pinned at the bottom.
     * @param sprite the fluid texture atlas sprite
     * @param b the precomputed fluid cuboid bounds
     * @param localHeight the total vat interior height in block units
     * @return a UV rect with V pinned at the bottom edge
     */
    private static GooRenderUtil.UvRect computeSideUv(TextureAtlasSprite sprite,
            CuboidBounds b, float localHeight) {
        float fillRatio = (b.yTop() - b.yBot()) / localHeight;
        return GooSubmitter.spriteSubRect(sprite, 0f, 1f - fillRatio, 1f, 1f);
    }

    /**
     * Computes how much of this vat's interior is filled, based on the
     * stack-wide fill fraction and this vat's position in the stack.
     *
     * @return fill height in block coords within [0, localHeight]
     *
     * @param state the block state
     * @param localFloor the local interior floor Y
     * @param localCeiling the local interior ceiling Y
     */
    static float computeLocalFill(VatRenderState state, float localFloor, float localCeiling) {
        float localHeight = localCeiling - localFloor;
        if (state.stackSize <= 1) {
            return state.fillFraction * localHeight;
        }

        float totalInterior = computeTotalInterior(state.stackSize);
        float cumulativeBelow = computeCumulativeBelow(state.indexFromBottom, state.stackSize);
        float globalFillHeight = state.fillFraction * totalInterior;

        return Math.max(0f, Math.min(globalFillHeight - cumulativeBelow, localHeight));
    }

    /**
     * Total interior height of a stack of N vats (in block units).
     *
     * @param stackSize the number of vats in the stack
     * @return the computed totalInterior
     */
    private static float computeTotalInterior(int stackSize) {
        if (stackSize <= 1) { return VatBlockEntityRenderer.CAP_CEILING - VatBlockEntityRenderer.BASE_FLOOR; }
        // Bottom (1.0-BASE_FLOOR) + middles (1.0 each) + top (CAP_CEILING)
        return 1.0f - VatBlockEntityRenderer.BASE_FLOOR + (stackSize - MIDDLE_EXCLUDED) * 1.0f + VatBlockEntityRenderer.CAP_CEILING;
    }

    /**
     * Cumulative interior height below the vat at indexFromBottom.
     *
     * @param index the zero-based index from the bottom
     * @param stackSize the number of vats in the stack
     * @return the computed cumulativeBelow
     */
    private static float computeCumulativeBelow(int index, int stackSize) {
        if (index == 0) { return 0f; }
        // Bottom vat contributes (1.0 - BASE_FLOOR)
        float below = 1.0f - VatBlockEntityRenderer.BASE_FLOOR;
        // Each middle vat below this one contributes 1.0
        int middlesBelowCount = index - 1; // index 1 = first middle, no extra middles below
        below += middlesBelowCount * 1.0f;
        return below;
    }
}
