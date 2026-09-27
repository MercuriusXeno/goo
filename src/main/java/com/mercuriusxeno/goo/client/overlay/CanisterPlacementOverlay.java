package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver;
import com.mercuriusxeno.goo.item.CanisterPlacementResolver.CanisterPlacement;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import org.jspecify.annotations.Nullable;

/**
 * Client-side overlay that renders a green wireframe where the held canister would land:
 * the slot the placement resolver answers, the same answer the server places by.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CanisterPlacementOverlay {

    /**
     * Translucent green color for the placement preview.
     */
    private static final int PREVIEW_COLOR = ARGB.color(180, 100, 255, 100);

    /**
     * Where the held canister would land this tick, or null when the use would place nothing.
     */
    private static @Nullable CanisterPlacement cachedPlacement;

    private CanisterPlacementOverlay() {
    }

    /**
     * Recomputes the placement preview target each client tick.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        cachedPlacement = computePlacement();
    }

    /**
     * Asks the placement resolver the server's placement also reads where the held canister
     * would land (decision preview-runs-the-placement-validator).
     *
     * @return the placement, or null when the use would place nothing
     */
    private static @Nullable CanisterPlacement computePlacement() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !(player.getMainHandItem().getItem() instanceof CanisterItem)) {
            return null;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            return null;
        }
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), hit);
        return CanisterPlacementResolver.resolve(context, player.isSecondaryUseActive());
    }

    /**
     * Hooks into the block outline event to render the placement preview.
     * Adds a non-suppressing renderer so the vanilla outline still draws.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onExtractOutline(ExtractBlockOutlineRenderStateEvent event) {
        CanisterPlacement placement = cachedPlacement;
        if (placement != null) {
            AABB bounds = CanisterBlock.slotShape(placement.slot()).bounds();
            event.addCustomRenderer(previewRendererAt(placement.pos(), bounds));
        }
    }

    /**
     * Creates a renderer that draws a wireframe at the given position and bounds.
     *
     * @param pos    the block position
     * @param bounds the axis-aligned bounding box
     * @return the custom outline renderer
     */
    private static net.neoforged.neoforge.client.CustomBlockOutlineRenderer previewRendererAt(
            BlockPos pos, AABB bounds) {
        return (renderState, bufferSource, poseStack, translucent, levelRenderState) ->
                renderPreview(renderState, bufferSource, poseStack, translucent,
                        levelRenderState, pos, bounds);
    }

    /**
     * Renders the wireframe preview at the target block position.
     *
     * @param renderState      the block outline render state
     * @param bufferSource     the buffer source for rendering
     * @param poseStack        the pose stack for rendering
     * @param translucent      whether the current pass is translucent
     * @param levelRenderState the level render state
     * @param targetPos        the target block position
     * @param bounds           the axis-aligned bounding box
     * @return false always (does not suppress other outline renderers)
     */
    private static boolean renderPreview(
            BlockOutlineRenderState renderState,
            MultiBufferSource.BufferSource bufferSource,
            PoseStack poseStack,
            boolean translucent,
            LevelRenderState levelRenderState,
            BlockPos targetPos, AABB bounds) {
        if (renderState.isTranslucent() != translucent) {
            return false;
        }

        Vec3 camPos = levelRenderState.cameraRenderState.pos;
        double ox = targetPos.getX() - camPos.x;
        double oy = targetPos.getY() - camPos.y;
        double oz = targetPos.getZ() - camPos.z;

        emitPreviewWireframe(bufferSource, poseStack, bounds, ox, oy, oz);
        return false;
    }

    /**
     * Draws the translucent green wireframe box at the camera-relative offset.
     *
     * @param bufferSource the buffer source for line rendering
     * @param poseStack    the pose stack for rendering
     * @param bounds       the slot bounding box in block-local coords
     * @param ox           the camera-relative X offset of the target block
     * @param oy           the camera-relative Y offset of the target block
     * @param oz           the camera-relative Z offset of the target block
     */
    private static void emitPreviewWireframe(MultiBufferSource.BufferSource bufferSource,
                                             PoseStack poseStack, AABB bounds, double ox, double oy, double oz) {
        float lineWidth = Minecraft.getInstance().getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), bufferSource.getBuffer(RenderTypes.lines()));
        ctx.emitWireframe(new CuboidBounds(
                        (float) (bounds.minX + ox), (float) (bounds.maxX + ox),
                        (float) (bounds.minZ + oz), (float) (bounds.maxZ + oz),
                        (float) (bounds.minY + oy), (float) (bounds.maxY + oy)),
                PREVIEW_COLOR, lineWidth);
        bufferSource.endLastBatch();
    }
}
