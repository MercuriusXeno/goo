package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import java.util.ArrayList;
import java.util.List;

/**
 * Fluid surface, stream, and body-side rendering helpers for {@link HubBlockEntityRenderer}.
 * Bodies and fluids submit through {@link GooSubmitter}, which owns the render
 * type, the lightmap rule and the sprite (decision shared-submission-entry-point).
 * Extracted to keep the parent BER under the PMD method-count threshold.
 */
final class HubFluidRenderer {

    /** Canister half-width: 2px. */
    private static final float HW = 2f / 16f;

    /** Top of lower gasket / bottom of body (y=3px). */
    private static final float BODY_BOT = 3f / 16f;

    /** Top of body / bottom of upper gasket (y=13px). */
    private static final float BODY_TOP = 13f / 16f;

    /** Inset from body walls to avoid z-fighting with fluid surfaces (0.5px). */
    private static final float FLUID_INSET = 0.5f / 16f;

    /** Shared fluid geometry constants for hub slots. */
    private static final SlotFluidGeometry.SlotGeometry FLUID_GEOM =
        new SlotFluidGeometry.SlotGeometry(HW, BODY_BOT, BODY_TOP, FLUID_INSET);

    /** Canister center positions in block coords (XZ), indexed by slot. */
    private static final float[][] CENTERS = {
        { 8f / 16f,  2f / 16f},   // slot 0 (N)
        {13f / 16f,  3f / 16f},   // slot 1 (NE)
        {14f / 16f,  8f / 16f},   // slot 2 (E)
        {13f / 16f, 13f / 16f},   // slot 3 (SE)
        { 8f / 16f, 14f / 16f},   // slot 4 (S)
        { 3f / 16f, 13f / 16f},   // slot 5 (SW)
        { 2f / 16f,  8f / 16f},   // slot 6 (W)
        { 3f / 16f,  3f / 16f},   // slot 7 (NW)
    };

    private HubFluidRenderer() {
    }

    // -- Body rendering --

    /**
     * Submits every present canister body through the submitter's sided-body
     * form in one draw call, at the block entity's world light.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitBodies(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        List<CuboidBounds> bodies = new ArrayList<>();
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slots[i].present) { bodies.add(bodyBounds(i)); }
        }
        if (bodies.isEmpty()) { return; }
        GooSubmitter.submitSidedBodies(poseStack, nodeCollector, state.lightCoords, bodies);
    }

    /**
     * Computes the canister body box at the given slot.
     *
     * @param slot the slot index
     * @return the body cuboid from body bottom to body top
     */
    private static CuboidBounds bodyBounds(int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        return new CuboidBounds(cx - HW, cx + HW, cz - HW, cz + HW, BODY_BOT, BODY_TOP);
    }

    // -- Fluid rendering --

    /**
     * Submits all fluid surfaces via the shared {@link SlottedFluidContainer} runner.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the render state snapshot
     */
    static void submitFluids(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        SlottedFluidContainer.submitFluids(poseStack, nodeCollector,
                state.slots, FLUID_GEOM, CENTERS, false);
    }

    // -- Stream rendering --

    /**
     * Batches all active stream cuboids into a single translucent draw call.
     *
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param state the block state
     */
    static void submitStreams(PoseStack poseStack,
            SubmitNodeCollector nodeCollector, HubRenderState state) {
        if (!hasAnyStream(state)) { return; }
        int light = state.lightCoords;
        float anim = state.animationTime;
        nodeCollector.submitCustomGeometry(poseStack, GooSubmitter.renderType(),
            (pose, c) -> renderAllStreams(new RenderContext(pose, c, light), anim, state));
    }

    /**
     * Renders stream segments for all active hub slots in a single batch.
     *
     * @param ctx   the render context
     * @param anim  the animation tick fraction
     * @param state the render state snapshot
     */
    private static void renderAllStreams(RenderContext ctx, float anim, HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slots[i].streamType == null) { continue; }
            renderSlotStream(ctx, anim, state, i);
        }
    }

    /**
     * Renders a single slot's goo stream segment.
     * @param ctx the render context
     * @param anim the animation tick fraction
     * @param state the render state snapshot
     * @param slot the slot index
     */
    private static void renderSlotStream(RenderContext ctx, float anim, HubRenderState state, int slot) {
        float cx = CENTERS[slot][0];
        float cz = CENTERS[slot][1];
        float yBottom = BODY_BOT + state.slots[slot].fill * (BODY_TOP - BODY_BOT);
        GooStreamRenderer.renderStream(ctx,
            cx, cz, BODY_TOP, yBottom,
            state.slots[slot].streamType, state.slots[slot].streamRate, anim);
    }

    /**
     * Returns true if any slot has an active stream.
     *
     * @param state the block state
     * @return true if anyStream is present
     */
    private static boolean hasAnyStream(HubRenderState state) {
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            if (state.slots[i].streamType != null) { return true; }
        }
        return false;
    }
}
