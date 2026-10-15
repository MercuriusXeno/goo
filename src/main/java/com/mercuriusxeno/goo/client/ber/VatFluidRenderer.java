package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.BandedSurfaceSubmitter;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.RenderContext;
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

    /** The sprite-relative UV rect spanning a whole sprite. */
    private static final GooRenderUtil.UvRect WHOLE_SPRITE = new GooRenderUtil.UvRect(0f, 0f, 1f, 1f);

    private VatFluidRenderer() {
    }

    /**
     * Renders this vat's portion of the unified fluid column once, top,
     * underside and side faces carrying every type band, so the shader
     * blends the types on one surface and the column reads mingled through
     * the glass (decision one-pass-blends-every-type).
     *
     * @param submitter submits the surface carrying every band
     * @param state     the block state
     */
    static void renderMingledFluid(BandedSurfaceSubmitter submitter, VatRenderState state) {
        submitter.submit(state.typeBands, ctx -> renderFluid(ctx, state));
    }

    /**
     * Renders this vat's portion of the unified fluid column with
     * sprite-relative UVs, which the shader maps into each type's sprite.
     * Computes local floor/ceiling from stack position, then determines how
     * much of this vat's interior is submerged.
     *
     * @param ctx   the render context
     * @param state the block state
     */
    static void renderFluid(RenderContext ctx, VatRenderState state) {
        float localFloor = state.vatBelow ? 0f : VatBlockEntityRenderer.BASE_FLOOR;
        float localCeiling = state.vatAbove ? 1.0f : VatBlockEntityRenderer.CAP_CEILING;
        float localFill = computeLocalFill(state, localFloor, localCeiling);
        if (localFill <= 0f) { return; }

        CuboidBounds b = computeVatCuboidBounds(state, localFloor, localFill);
        renderVatTopFaces(ctx, b, localCeiling - localFloor, localFill, state.rippleAmplitude);
        renderVatSideFaces(ctx, b, localCeiling - localFloor);
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
     * @param localHeight the total vat height in block units
     * @param localFill   the fill height in block units
     * @param amplitude   the ripple amplitude the interior vertices carry
     */
    private static void renderVatTopFaces(RenderContext ctx, CuboidBounds b,
                                          float localHeight, float localFill, float amplitude) {
        boolean isFullySubmerged = localFill >= localHeight - SUBMERSION_EPSILON;
        if (isFullySubmerged) { return; }
        ctx.liquidSurfaceGrid(b, WHOLE_SPRITE, amplitude);
        ctx.liquidSurfaceGridDown(b, WHOLE_SPRITE, amplitude);
    }

    /**
     * Renders the four side faces with UV pinned at the bottom.
     *
     * @param ctx         the render context
     * @param b           the precomputed fluid cuboid bounds
     * @param localHeight the total vat height in block units
     */
    private static void renderVatSideFaces(RenderContext ctx, CuboidBounds b, float localHeight) {
        GooRenderUtil.UvRect uv = computeSideUv(b, localHeight);
        ctx.emitFace(b, uv, Direction.NORTH);
        ctx.emitFace(b, uv, Direction.SOUTH);
        ctx.emitFace(b, uv, Direction.WEST);
        ctx.emitFace(b, uv, Direction.EAST);
    }

    /**
     * Computes the sprite-relative UV rect for side faces with the V range pinned at the bottom.
     * @param b the precomputed fluid cuboid bounds
     * @param localHeight the total vat interior height in block units
     * @return a UV rect with V pinned at the bottom edge
     */
    private static GooRenderUtil.UvRect computeSideUv(CuboidBounds b, float localHeight) {
        float fillRatio = (b.yTop() - b.yBot()) / localHeight;
        return new GooRenderUtil.UvRect(0f, 1f - fillRatio, 1f, 1f);
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
