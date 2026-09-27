package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import com.mercuriusxeno.goo.item.CanisterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.CustomBlockOutlineRenderer;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Custom block outline renderer for every canister holder. Draws the outline the
 * holder answers instead of its full selection shape, a red pickup highlight on the
 * filled slot a standing click would take, and a green placement preview on the empty
 * slot a held canister would enter (decision hosts-answer-bounds-through-interfaces).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class SlotOutlineRenderer {

    private SlotOutlineRenderer() {
    }

    /**
     * Intercepts outline extraction for a canister holder, adding a renderer that
     * draws the holder's outline, pickup highlight and placement preview.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getLevel().getBlockEntity(event.getBlockPos()) instanceof ICanisterHolder holder)) {
            return;
        }
        BlockHitResult hit = event.getHitResult();
        var player = Minecraft.getInstance().player;
        boolean sneaking = player != null && player.isSecondaryUseActive();
        VoxelShape outline = holder.outlineShape(hit);
        AABB pickup = player != null && !sneaking ? holder.pickupBounds(hit) : null;
        AABB preview = isHoldingCanister() ? holder.previewBounds(hit, sneaking) : null;
        event.addCustomRenderer(slotRenderer(outline, preview, pickup));
    }

    /**
     * Returns true if the local player is holding a canister item in their main hand.
     *
     * @return true if the player holds a canister
     */
    private static boolean isHoldingCanister() {
        var player = Minecraft.getInstance().player;
        return player != null && player.getMainHandItem().getItem() instanceof CanisterItem;
    }

    /**
     * Creates a custom outline renderer with optional green preview and red pickup highlight.
     *
     * @param shape   the voxel shape to render
     * @param preview the placement preview bounds (green), or null
     * @param pickup  the pickup highlight bounds (red), or null
     * @return the custom outline renderer
     */
    private static CustomBlockOutlineRenderer slotRenderer(
            VoxelShape shape, @Nullable AABB preview, @Nullable AABB pickup) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
                SlotOutlineDrawing.renderOutline(renderState, bufferSource, poseStack, translucent,
                        levelRenderState, shape, preview, pickup);
    }
}
