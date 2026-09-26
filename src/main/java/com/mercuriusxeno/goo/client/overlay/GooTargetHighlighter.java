package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.hud.ChainMarkerBillboard;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;

/**
 * Draws the glove's aim each frame from the tick's {@link AimTracker}: a
 * goo-colored highlight on the targeted block or chain marker at the opaque
 * stage, and the throw arc after translucent blocks. The entity outline
 * rides the render state modifier AimTracker registers (decision
 * render-context-is-the-one-emitter).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {

    /**
     * Target the opaque stage leaves for the translucent arc stage, or null
     * when the frame drew none or the arc stage already took it.
     */
    private static @Nullable TargetResult cachedArcTarget;
    /**
     * Goo type for the cached arc target.
     */
    private static @Nullable ResourceKey<GooTypeDefinition> cachedArcType;
    /**
     * Partial tick captured at the opaque stage.
     */
    private static float cachedArcPartialTick;

    private GooTargetHighlighter() {
    }

    /**
     * Renders the targeted block or chain marker highlight and leaves the
     * target for the arc stage.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        clearCachedArc();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(mc.player);
        if (selectedType == null) {
            return;
        }
        TargetResult target = AimTracker.currentTarget();
        cachedArcTarget = target;
        cachedArcType = selectedType;
        cachedArcPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HighlightFrame frame = new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), mc, selectedType);
        renderTargetHighlight(target, frame);
    }

    /**
     * What one frame's highlight draws with.
     *
     * @param ps           the pose stack
     * @param buf          the buffer source
     * @param camera       the render camera
     * @param mc           the Minecraft client instance
     * @param selectedType the selected goo type
     */
    private record HighlightFrame(PoseStack ps, MultiBufferSource.BufferSource buf, Camera camera,
                                  Minecraft mc, ResourceKey<GooTypeDefinition> selectedType) {
        /**
         * The client level the frame reads.
         *
         * @return the level
         */
        Level level() {
            return mc.level;
        }

        /**
         * Outlines the voxel shape of the block at the given position.
         *
         * @param pos the block position
         */
        void outlineShape(BlockPos pos) {
            VoxelHighlightRenderer.renderBlockShape(ps, buf, camera, pos, selectedType);
        }

        /**
         * Draws the stack-count billboard above the chain marker at the given position.
         *
         * @param pos     the chain marker position
         * @param gooType the goo type for the icon
         */
        void billboard(BlockPos pos, ResourceKey<GooTypeDefinition> gooType) {
            ChainMarkerBillboard.render(ps, buf, camera, mc.level, mc.font, pos, gooType);
        }
    }

    /**
     * Dispatches highlight rendering based on target type.
     *
     * @param target the aim target
     * @param frame  what the frame draws with
     */
    private static void renderTargetHighlight(TargetResult target, HighlightFrame frame) {
        if (target instanceof TargetResult.BlockTarget bt) {
            renderBlockTargetHighlight(bt, frame);
        } else if (target instanceof TargetResult.ChainMarkerTarget cmt) {
            renderChainMarkerHighlight(cmt.pos(), frame);
        } else if (target instanceof TargetResult.GlowCrystalTarget gct) {
            frame.outlineShape(gct.pos());
        }
    }

    /**
     * Renders highlight for a block target, detecting adjacent chain markers.
     *
     * @param bt    the block target
     * @param frame what the frame draws with
     */
    private static void renderBlockTargetHighlight(TargetResult.BlockTarget bt, HighlightFrame frame) {
        BlockPos markerPos = TargetBlockReads.adjacentMarker(frame.level(), bt.pos(), bt.face(),
                GloveAim.selectedAbilityId(frame.mc().player));
        if (markerPos != null) {
            if (TargetBlockReads.canAcceptMoreBlobs(frame.level(), markerPos)) {
                frame.outlineShape(markerPos);
            }
            frame.billboard(markerPos, frame.selectedType());
        } else if (TargetBlockReads.isWaterSource(frame.level(), bt.pos())) {
            VoxelHighlightRenderer.renderFullCube(frame.ps(), frame.buf(), frame.camera(), bt.pos(),
                    frame.selectedType());
        } else {
            frame.outlineShape(bt.pos());
        }
    }

    /**
     * Renders highlight for a directly targeted chain marker.
     *
     * @param pos   the chain marker position
     * @param frame what the frame draws with
     */
    private static void renderChainMarkerHighlight(BlockPos pos, HighlightFrame frame) {
        if (TargetBlockReads.canAcceptMoreBlobs(frame.level(), pos)) {
            frame.outlineShape(pos);
        }
        ResourceKey<GooTypeDefinition> blobType = TargetBlockReads.markerGooType(frame.level(), pos);
        frame.billboard(pos, blobType != null ? blobType : frame.selectedType());
    }

    /**
     * Renders the deferred throw-arc line after translucent blocks so
     * the depth buffer contains both opaque and water depth for
     * correct sorting. A granny arc only follows a block target
     * classified as an upper-edge hit.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        TargetResult target = cachedArcTarget;
        ResourceKey<GooTypeDefinition> type = cachedArcType;
        float partialTick = cachedArcPartialTick;
        clearCachedArc();
        Minecraft mc = Minecraft.getInstance();
        if (target == null || type == null) {
            return;
        }
        Vec3 end = target.resolveEndpoint();
        if (end == null) {
            return;
        }
        boolean grannyArc = target instanceof TargetResult.BlockTarget bt && bt.grannyArc();
        ArcRenderer.renderTargetArc(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), end, ClientGooTypes.highlight(type),
                partialTick, grannyArc, type == GooTypes.GLOW);
    }

    /**
     * Drops the target the arc stage would draw.
     */
    private static void clearCachedArc() {
        cachedArcTarget = null;
        cachedArcType = null;
        cachedArcPartialTick = 0f;
    }
}
