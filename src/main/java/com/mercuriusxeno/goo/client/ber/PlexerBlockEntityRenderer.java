package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Renders the plexer's set target floating in its cutaway, slightly faded; an empty target draws
 * nothing. No held item previews there, since no held item targets the plexer
 * (decision ghost-item-and-g-key-retire).
 */
public class PlexerBlockEntityRenderer
        implements BlockEntityRenderer<PlexerBlockEntity, PlexerRenderState> {

    /** Block center on X/Z axes (rotation pivot). */
    private static final float BLOCK_CENTER = 0.5f;

    /** Cutaway center Y in model space: midpoint of [8,13]. */
    private static final float CUTAWAY_Y = 10.5f / 16f;

    /** Z offset from block center to cutaway center (2/16 - 8/16). */
    private static final float CUTAWAY_Z_OFFSET = -6f / 16f;

    /** Item scale - roughly 5px in a 16px space. */
    private static final float ITEM_SCALE = 0.3f;

    /** Alpha the set target draws at, slightly faded. */
    private static final int SETTLED_ALPHA = 210;

    private final ItemModelResolver itemModelResolver;
    private final ItemStackRenderState targetRenderState = new ItemStackRenderState();

    /**
     * @param context the renderer provider context
     */
    public PlexerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModelResolver = context.itemModelResolver();
    }

    @Override
    public PlexerRenderState createRenderState() {
        return new PlexerRenderState();
    }

    /**
     * Snapshots the target item and the facing from the block entity.
     *
     * @param be            the block entity instance
     * @param state         the render state to populate
     * @param partialTick   the partial tick for interpolation
     * @param cameraPos     the camera world position
     * @param breakProgress the crumbling overlay, or null
     */
    @Override
    public void extractRenderState(PlexerBlockEntity be, PlexerRenderState state,
            float partialTick, Vec3 cameraPos,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(be, state, breakProgress);
        state.targetItem = be.getTargetItem();
        state.facing = be.getBlockState().getValue(PlexerBlock.FACING);
    }

    /**
     * Draws the set target in the cutaway.
     *
     * @param state         the render state
     * @param poseStack     the pose stack
     * @param nodeCollector the render node collector
     * @param cameraState   the camera render state
     */
    @Override
    public void submit(PlexerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector, CameraRenderState cameraState) {
        if (state.targetItem.isEmpty()) {
            return;
        }
        itemModelResolver.updateForTopItem(targetRenderState, state.targetItem, ItemDisplayContext.FIXED, null, null, 0);
        if (targetRenderState.isEmpty()) {
            return;
        }
        TranslucentItemCollector proxy = new TranslucentItemCollector(nodeCollector, SETTLED_ALPHA);
        poseStack.pushPose();
        translateToCutaway(poseStack, state.facing);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        targetRenderState.submit(poseStack, proxy, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    /**
     * Translates the pose stack to the cutaway center for the given facing.
     *
     * @param poseStack the pose stack
     * @param facing    the block facing direction
     */
    private static void translateToCutaway(PoseStack poseStack, Direction facing) {
        poseStack.translate(BLOCK_CENTER, CUTAWAY_Y, BLOCK_CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        poseStack.translate(0f, 0f, CUTAWAY_Z_OFFSET);
    }
}
