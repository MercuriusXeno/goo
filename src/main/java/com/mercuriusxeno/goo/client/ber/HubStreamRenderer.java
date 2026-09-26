package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;

/**
 * Stream rendering for {@link HubBlockEntityRenderer}: the goo falling from a
 * hub pipe into each canister, from the body top down to the fluid surface.
 * Streams submit through {@link GooSubmitter}, which owns the render type
 * (decision shared-submission-entry-point).
 */
final class HubStreamRenderer {

    private HubStreamRenderer() {
    }

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
     *
     * @param ctx the render context
     * @param anim the animation tick fraction
     * @param state the render state snapshot
     * @param slot the slot index
     */
    private static void renderSlotStream(RenderContext ctx, float anim, HubRenderState state, int slot) {
        CanisterGeometry geometry = state.canisterGeometry();
        float[] center = HubRenderState.SLOT_CENTERS[slot];
        GooStreamRenderer.renderStream(ctx,
            center[0], center[1], geometry.bodyTop(), geometry.fluidSurface(state.slots[slot].fill),
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
