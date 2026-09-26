package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.canister.CanisterGeometry;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the canister sitting on a tap's body slot through the shared
 * {@link CanisterSlotRenderer}, positioned at the per-facing canister slot center.
 */
public class TapBlockEntityRenderer
        implements BlockEntityRenderer<TapBlockEntity, TapRenderState> {

    /**
     * Divisor for computing AABB center from min+max.
     */
    private static final double CENTER_DIVISOR = 2.0;

    /**
     * Creates a tap BER.
     *
     * @param context the renderer provider context
     */
    public TapBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Reads compression level and goo fill from the canister stack.
     *
     * @param canister the canister item stack
     * @param state    the render state to populate
     */
    private static void extractCanisterContents(ItemStack canister, TapRenderState state) {
        state.slot.compression = GooEnchantments.getCompressionLevel(canister);
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (content.isEmpty()) {
            clearContents(state);
        } else {
            extractNonEmptyContents(content, state);
        }
    }

    /**
     * Populates goo type and fill ratio from non-empty canister contents.
     *
     * @param content the non-empty canister fluid content
     * @param state   the render state to populate
     */
    private static void extractNonEmptyContents(CanisterFluidContent content, TapRenderState state) {
        int capacity = ContainerCapacity.canisterCapacity(state.slot.compression);
        state.slot.type = content.getGooType();
        state.slot.fill = Math.min(1f, (float) content.amount() / capacity);
    }

    /**
     * Resets goo type and fill to empty defaults.
     *
     * @param state the render state to clear
     */
    private static void clearContents(TapRenderState state) {
        state.slot.type = null;
        state.slot.fill = 0f;
    }

    @Override
    public TapRenderState createRenderState() {
        return new TapRenderState();
    }

    /**
     * Snapshots canister presence and fluid data from the block entity. The
     * tap draws its canister with copper caps on both ends whatever gaskets the
     * canister carries: the tap's own gasket is the block-level one, so the
     * slot never reports a cap gasket.
     *
     * @param be            the block entity instance
     * @param state         the block state
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(TapBlockEntity be, TapRenderState state, float partialTick,
                                   Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(TapBlock.FACING);
        state.slot.present = !be.getCanister().isEmpty();
        state.slot.topGasketPresent = false;
        state.slot.bottomGasketPresent = false;
        if (state.slot.present) {
            extractCanisterContents(be.getCanister(), state);
        } else {
            clearContents(state);
        }
    }

    /**
     * Submits the canister's body, caps and fluid if a canister is present.
     *
     * @param state         the block state
     * @param poseStack     the pose stack for rendering
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(TapRenderState state, PoseStack poseStack,
                       SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (!state.slot.present) {
            return;
        }
        AABB sb = TapBlock.canisterSlotShape(state.facing).bounds();
        float[][] center = {{
            (float) ((sb.minX + sb.maxX) / CENTER_DIVISOR),
            (float) ((sb.minZ + sb.maxZ) / CENTER_DIVISOR)}};
        SlotState[] slots = {state.slot};
        CanisterGeometry geometry = state.canisterGeometry();
        CanisterSlotRenderer.submitBodies(poseStack, nodeCollector, state.lightCoords, geometry, slots, center);
        CanisterSlotRenderer.submitCaps(poseStack, nodeCollector, state.lightCoords, geometry, slots, center);
        CanisterSlotRenderer.submitFluids(poseStack, nodeCollector, geometry, slots, center, false);
    }
}
