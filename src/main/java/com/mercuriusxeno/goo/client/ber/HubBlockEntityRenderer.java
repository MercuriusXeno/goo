package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders canisters attached to hub pipe slots. Each occupied slot
 * draws a canister body with gasket caps at the pipe center position,
 * hanging from the pipe level down to the hub base.
 *
 * <h3>Blockbench Slot Geometry Reference (pixel units, 1/16 block)</h3>
 * <p>Slots 0-5 have finalized geometry. Slots 6-7 (W, NW) are TBD.</p>
 * <pre>
 * Slot 0 (N, 0 deg):
 *   Pipe: [7,11.01,1.25]-[9.1,11.99,6.35] rot=0 origin=[8,11.5,2.75]
 *   Upper gasket: [6,10,0.25]-[10,11,4.25]   Body: [6,3,0.25]-[10,10,4.25]   Lower: [6,2,0.25]-[10,3,4.25]
 * Slot 1 (67.5 deg):
 *   Pipe: [2.75,11,3.9]-[4.85,11.98,9] rot=67.5 origin=[3.75,11.5,5.5]
 *   Upper: [1.25,10,3.25]-[5.25,11,7.25] rot=67.5   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 2 (112.5 deg):
 *   Pipe: [2.7,11,8.9]-[4.8,11.98,14] rot=112.5 origin=[3.75,11.5,10.5]
 *   Upper: [1.25,10,8.75]-[5.25,11,12.75] rot=112.5   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 3 (S, 180 deg):
 *   Pipe: [7,11.01,9.75]-[9.1,11.99,14.85] rot=0 origin=[8,11.5,13.25]
 *   Upper: [8,10,13.75]-[12,11,17.75] rot=-180   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 4 (-115 deg / 245 deg):
 *   Pipe: [11.2,11,8.9]-[13.3,11.98,14] rot=-115 origin=[12.25,11.5,10.5]
 *   Upper: [10.75,10,8.75]-[14.75,11,12.75] rot=-115   Body/Lower: same XZ, Y=3-10/2-3
 * Slot 5 (-67.5 deg / 292.5 deg):
 *   Pipe: [11.15,11,3.9]-[13.25,11.98,9] rot=-67.5 origin=[12.25,11.5,5.5]
 *   Upper: [10.75,10,3.25]-[14.75,11,7.25] rot=-67.5   Body/Lower: same XZ, Y=3-10/2-3
 * Textures: gaskets=#0 (choral_gasket), body=#1 (canister_side)
 * UV caps: up=[8,4,4,0] down=[8,0,4,4] from #1
 * </pre>
 */
public class HubBlockEntityRenderer
        implements BlockEntityRenderer<HubBlockEntity, HubRenderState> {

    /**
     * Creates a hub BER. Context is unused.
     *
     * @param context the renderer provider context
     */
    public HubBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public HubRenderState createRenderState() {
        return new HubRenderState();
    }

    /**
     * Snapshots canister presence, fluid fill, and stream state from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param partialTick the partial tick for interpolation
     * @param cameraPos the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(HubBlockEntity be, HubRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        long gameTick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        state.animationTime = gameTick + partialTick;
        for (int i = 0; i < HubBlockEntity.MAX_CANISTERS; i++) {
            extractSlot(be, state, i, gameTick);
        }
    }

    /**
     * Extracts all render data for a single hub canister slot.
     *
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlot(HubBlockEntity be, HubRenderState state,
            int slot, long gameTick) {
        ItemStack stack = be.getCanister(slot);
        state.slots[slot].present = !stack.isEmpty();
        CanisterMetadata meta = CanisterItem.getMetadata(stack);
        state.slots[slot].topGasketPresent = meta.topGasketId() != null;
        state.slots[slot].bottomGasketPresent = meta.bottomGasketId() != null;
        extractSlotFluid(be, state, slot);
        extractSlotStream(be, state, slot, gameTick);
    }

    /**
     * Extracts dominant goo type and fill fraction for a single hub slot.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     */
    private static void extractSlotFluid(HubBlockEntity be, HubRenderState state, int slot) {
        CanisterFluidContent content = be.getSlotFluidContent(slot);
        if (content.isEmpty()) {
            state.slots[slot].type = null;
            state.slots[slot].fill = 0f;
            return;
        }
        populateFilledSlot(be, state, slot, content);
    }

    /**
     * Populates render state for a slot with goo contents.
     * @param be the block entity instance
     * @param state the render state snapshot
     * @param slot the slot index
     * @param content the non-empty fluid content for this slot
     */
    private static void populateFilledSlot(HubBlockEntity be, HubRenderState state,
            int slot, CanisterFluidContent content) {
        int capacity = ContainerCapacity.canisterCapacity(GooEnchantments.getCompressionLevel(be.getCanister(slot)));
        state.slots[slot].type = content.getGooType();
        state.slots[slot].fill = Math.min(1f, (float) content.amount() / capacity);
    }

    /**
     * Extracts active stream data for a single slot from the block entity.
     *
     * @param be the block entity instance
     * @param state the block state
     * @param slot the slot index
     * @param gameTick the current game tick
     */
    private static void extractSlotStream(HubBlockEntity be,
            HubRenderState state, int slot, long gameTick) {
        state.slots[slot].streamType = be.containerState().getSlotStreamType(slot, gameTick);
        state.slots[slot].streamRate = be.containerState().getSlotStreamRate(slot, gameTick);
    }

    /**
     * Submits canister geometry for all occupied slots.
     *
     * @param state the block state
     * @param poseStack the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState the camera render state
     */
    @Override
    public void submit(HubRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (!hasAnyCanister(state)) { return; }
        CanisterGeometry geometry = state.canisterGeometry();
        float[][] centers = HubRenderState.SLOT_CENTERS;
        CanisterSlotRenderer.submitBodies(poseStack, nodeCollector, state.lightCoords, geometry, state.slots, centers);
        CanisterSlotRenderer.submitCaps(poseStack, nodeCollector, state.lightCoords, geometry, state.slots, centers);
        CanisterSlotRenderer.submitFluids(poseStack, nodeCollector, geometry, state.slots, centers, false);
        HubStreamRenderer.submitStreams(poseStack, nodeCollector, state);
    }

    /**
     * Returns true if any slot has a canister.
     *
     * @param state the block state
     * @return true if anyCanister is present
     */
    private static boolean hasAnyCanister(HubRenderState state) {
        for (SlotState slot : state.slots) {
            if (slot.present) { return true; }
        }
        return false;
    }
}
