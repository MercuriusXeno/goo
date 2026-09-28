package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooColors;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.block.crystallizer.CrystalCluster;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the quartz cluster growing from the crystallizer's purple spot in the
 * growing type's own fluid texture (decision crystallizer-emits-chrysm), turned
 * with its facing. The canisters on its top stand in a canister block, which
 * draws them.
 */
public class CrystallizerBlockEntityRenderer
        implements BlockEntityRenderer<CrystallizerBlockEntity, CrystallizerRenderState> {

    private static final float BLOCK_CENTER = 0.5f;

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
        state.crystalGrowth = be.drawnGrowth(partialTick);
        ResourceKey<GooTypeDefinition> type = be.formingType();
        state.crystalLook = type == null || be.getLevel() == null ? null
                : CrystalClusterSubmitter.lookOf(type, GooColors.get(be.getLevel().registryAccess(), type));
    }

    @Override
    public void submit(CrystallizerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        poseStack.pushPose();
        rotateToFacing(poseStack, state.facing);
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
