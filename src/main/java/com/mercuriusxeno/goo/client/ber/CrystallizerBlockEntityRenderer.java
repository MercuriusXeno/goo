package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.item.CanisterFluidContent;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterMetadata;
import com.mercuriusxeno.goo.item.ContainerCapacity;
import com.mercuriusxeno.goo.registry.GooEnchantments;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the two canisters standing on the crystallizer's top, back left and
 * back right, and the quartz cluster growing from its purple spot in the growing
 * type's own fluid texture (decision crystallizer-emits-chrysm), all turned with
 * its facing.
 */
public class CrystallizerBlockEntityRenderer
        implements BlockEntityRenderer<CrystallizerBlockEntity, CrystallizerRenderState> {

    private static final float BLOCK_CENTER = 0.5f;

    /** The slots' centers in model space, the dial on the south face: back left then back right. */
    private static final float[][] SLOT_CENTERS = {{5f / 16f, 5f / 16f}, {11f / 16f, 5f / 16f}};

    /**
     * @param context the renderer provider context
     */
    public CrystallizerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public CrystallizerRenderState createRenderState() {
        return new CrystallizerRenderState();
    }

    @Override
    public void extractRenderState(CrystallizerBlockEntity be, CrystallizerRenderState state,
            float partialTick, Vec3 cameraPos, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.facing = be.getBlockState().getValue(CrystallizerBlock.FACING);
        if (be.getLevel() != null) {
            state.lightCoords = LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos().above());
        }
        for (int slot = 0; slot < state.slots.length; slot++) {
            extractSlot(be.getCanister(slot), state.slots[slot]);
        }
        state.crystalGrowth = be.drawnGrowth(partialTick);
        ResourceKey<GooTypeDefinition> type = be.formingType();
        state.crystalLook = type == null || be.getLevel() == null ? null
                : CrystalClusterSubmitter.lookOf(type, GooColors.get(be.getLevel().registryAccess(), type));
    }

    /**
     * Reads presence, compression, gasket caps and goo fill from one canister.
     *
     * @param canister the canister stack, or EMPTY
     * @param slot     the slot snapshot to fill
     */
    private static void extractSlot(ItemStack canister, SlotState slot) {
        slot.present = !canister.isEmpty();
        slot.type = null;
        slot.fill = 0f;
        slot.topGasketPresent = false;
        slot.bottomGasketPresent = false;
        if (!slot.present) {
            return;
        }
        slot.compression = GooEnchantments.getCompressionLevel(canister);
        CanisterMetadata meta = CanisterItem.getMetadata(canister);
        slot.topGasketPresent = meta.topGasketId() != null;
        slot.bottomGasketPresent = meta.bottomGasketId() != null;
        CanisterFluidContent content = CanisterItem.getFluidContent(canister);
        if (!content.isEmpty()) {
            slot.type = content.getGooType();
            slot.fill = Math.min(1f, (float) content.amount() / ContainerCapacity.canisterCapacity(slot.compression));
        }
    }

    @Override
    public void submit(CrystallizerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        poseStack.pushPose();
        rotateToFacing(poseStack, state.facing);
        CanisterSlotRenderer.submitBodies(poseStack, nodeCollector, state.lightCoords, state.canisterGeometry(),
                state.slots, SLOT_CENTERS);
        CanisterSlotRenderer.submitCaps(poseStack, nodeCollector, state.lightCoords, state.canisterGeometry(),
                state.slots, SLOT_CENTERS);
        CanisterSlotRenderer.submitFluids(poseStack, nodeCollector, state.canisterGeometry(), state.slots,
                SLOT_CENTERS, false);
        CrystalClusterSubmitter.Look look = state.crystalLook;
        if (look != null) {
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, CrystalCluster.prisms(state.crystalGrowth), look,
                    state.lightCoords);
        }
        poseStack.popPose();
    }

    /**
     * Turns the south-facing model space to the block's facing, as the blockstate's y rotation does.
     *
     * @param poseStack the pose stack
     * @param facing    the face the dial sits on
     */
    private static void rotateToFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, 0, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(-BLOCK_CENTER, 0, -BLOCK_CENTER);
    }
}
