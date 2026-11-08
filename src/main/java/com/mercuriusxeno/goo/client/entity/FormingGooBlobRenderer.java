package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.entity.FormingGooBlob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a forming goo blob as the goo item it becomes, hovering and turning
 * as a dropped item does, at the size the goo it holds gives it: nothing
 * while empty, easing larger as each block's goo fills it, then easing down
 * to a goo item's own size before the item takes its place.
 * unmake-converges-near-the-unmade-blocks-into-a-goo-item
 */
public final class FormingGooBlobRenderer extends EntityRenderer<FormingGooBlob, FormingGooBlobRenderer.State> {

    private static final float SPIN_DEGREES_PER_TICK = 2f;

    private final ItemModelResolver itemModelResolver;

    /**
     * What one frame draws a blob with.
     */
    public static final class State extends EntityRenderState {
        /** The goo item it shows. */
        final ItemStackRenderState item = new ItemStackRenderState();
        /** How large it draws, 1 for a goo item's size. */
        float scale;
    }

    /**
     * @param context the renderer context
     */
    public FormingGooBlobRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
    }

    /** A blob forms in a block being unmade, whose own cell reads dark, so the cell above it lights it too. */
    @Override
    protected int getBlockLightLevel(FormingGooBlob blob, BlockPos pos) {
        return Math.max(super.getBlockLightLevel(blob, pos), super.getBlockLightLevel(blob, pos.above()));
    }

    @Override
    protected int getSkyLightLevel(FormingGooBlob blob, BlockPos pos) {
        return Math.max(super.getSkyLightLevel(blob, pos), super.getSkyLightLevel(blob, pos.above()));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(FormingGooBlob blob, State state, float partialTick) {
        super.extractRenderState(blob, state, partialTick);
        itemModelResolver.updateForTopItem(state.item, blob.shown(), ItemDisplayContext.GROUND, blob.level(), blob,
                blob.getId());
        state.scale = blob.shownScale(partialTick);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.item.isEmpty() && state.scale > 0) {
            Vec3 center = state.item.getModelBoundingBox().getCenter();
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(state.ageInTicks * SPIN_DEGREES_PER_TICK));
            poseStack.scale(state.scale, state.scale, state.scale);
            poseStack.translate(-center.x, -center.y, -center.z);
            state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
        super.submit(state, poseStack, collector, camera);
    }
}
