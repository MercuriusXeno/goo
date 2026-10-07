package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
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
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;

/**
 * Draws the glove's aim each frame from the frame's {@link AimTracker}: a
 * goo-colored highlight on the targeted block or ability block at the opaque
 * stage, and the throw arc after translucent blocks. The entity outline
 * rides the render state modifier AimTracker registers (decision
 * render-context-is-the-one-emitter).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {

    private static final double NANOS_PER_SECOND = 1e9;

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
     * Delivery of the selected ability for the cached arc target.
     */
    private static @Nullable Delivery cachedArcDelivery;
    /**
     * Whether the selected ability touches an entity within reach, so the
     * arc stage draws the touch ring.
     */
    private static boolean cachedArcTouchesAtReach;
    /**
     * Partial tick captured at the opaque stage.
     */
    private static float cachedArcPartialTick;

    /** Whether this frame drew the reticule, which then stands in for the vanilla crosshair. */
    private static boolean reticuleDrawn;

    private GooTargetHighlighter() {
    }

    /**
     * Hides the vanilla crosshair while the reticule marks the aim, so one
     * crosshair shows rather than two.
     * target-kind-configured-per-ability
     *
     * @param event the gui layer event
     */
    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (reticuleDrawn && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    /**
     * Renders the targeted block or ability block highlight and leaves the
     * target for the arc stage.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        clearCachedArc();
        reticuleDrawn = false;
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
        String abilityId = GloveAim.selectedAbilityId(mc.player);
        cachedArcDelivery = GloveThrowSender.selectedDelivery(abilityId);
        cachedArcTouchesAtReach = GloveThrowSender.selectedTouchesAtReach(abilityId);
        cachedArcPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HighlightFrame frame = new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), mc, selectedType);
        renderIndicator(target, AimIndicator.of(GloveThrowSender.selectedBadge(abilityId)), frame);
    }

    /**
     * Draws the mark the selected ability's badge chooses: a reticule at the
     * aimed point, the outline of the block or crystal aimed at, or nothing.
     * target-kind-configured-per-ability
     *
     * @param target    the aim target
     * @param indicator the mark the badge chooses
     * @param frame     what the frame draws with
     */
    private static void renderIndicator(TargetResult target, AimIndicator indicator, HighlightFrame frame) {
        switch (indicator) {
            case NONE -> { }
            case RETICULE -> renderReticule(target, frame);
            case ENTITY_OUTLINE, BLOCK_OUTLINE -> renderTargetHighlight(target, frame);
        }
    }

    /**
     * Draws the reticule at the aimed point, standing in for the vanilla
     * crosshair, and the tile's outline and bullseye where the point sits on a block.
     *
     * @param target the aim target
     * @param frame  what the frame draws with
     */
    private static void renderReticule(TargetResult target, HighlightFrame frame) {
        Vec3 point = target.point();
        if (point != null) {
            ReticuleRenderer.render(frame.ps(), frame.buf(), frame.camera(), point,
                    ClientGooTypes.highlight(frame.selectedType()));
            reticuleDrawn = true;
        }
        if (target instanceof TargetResult.PointTarget pt && pt.onBlock()) {
            renderBlockTargetHighlight(pt.tile(), frame);
        }
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
         * Draws the bullseye on the target's struck face, taken from the
         * resolved target rather than the eased arc endpoint (decision
         * aim-arc-ends-in-face-bullseye).
         *
         * @param target the aim target
         */
        void bullseye(TargetResult target) {
            FaceBullseyeRenderer.render(ps, buf, camera, target, ClientGooTypes.highlight(selectedType),
                    realTimeSeconds());
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
        } else if (target instanceof TargetResult.GlowCrystalTarget gct) {
            frame.outlineShape(gct.pos());
        }
    }

    /**
     * Renders highlight for a block target.
     *
     * @param bt    the block target
     * @param frame what the frame draws with
     */
    private static void renderBlockTargetHighlight(TargetResult.BlockTarget bt, HighlightFrame frame) {
        if (TargetBlockReads.isWaterSource(frame.level(), bt.pos())) {
            VoxelHighlightRenderer.renderFullCube(frame.ps(), frame.buf(), frame.camera(), bt.pos(),
                    frame.selectedType());
            frame.bullseye(bt);
        } else {
            frame.outlineShape(bt.pos());
            frame.bullseye(bt);
        }
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
        Delivery delivery = cachedArcDelivery;
        float partialTick = cachedArcPartialTick;
        boolean touchesAtReach = cachedArcTouchesAtReach;
        clearCachedArc();
        if (target == null || type == null || delivery == null || !delivery.aimsALine()) {
            return;
        }
        if (touchesAtReach) {
            renderTouchRing(event, type, partialTick);
        }
        renderAimLine(event, target, type, delivery, partialTick);
    }

    /**
     * Draws the aim line to the exact point aimed at, the same frame the tile
     * highlight draws, with nothing carried from the last frame.
     * aim-line-snaps-with-the-tile-highlight
     * aim-point-follows-the-cursor
     *
     * @param event       the render stage event
     * @param target      the target this frame aims at
     * @param type        the selected goo type
     * @param delivery    the selected delivery, an arc or a beam
     * @param partialTick the partial tick captured at the opaque stage
     */
    private static void renderAimLine(RenderLevelStageEvent.AfterTranslucentBlocks event, TargetResult target,
                                      ResourceKey<GooTypeDefinition> type, Delivery delivery, float partialTick) {
        Vec3 end = target.point();
        if (end == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ArcRenderer.renderTargetArc(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), end, ClientGooTypes.highlight(type),
                partialTick, grannyWeight(target, delivery), delivery.fliesStraight());
    }

    /**
     * Draws the ring a mob ability touches within, the player's entity
     * interaction range around the player's feet.
     * decision mob-ability-touches-at-reach
     *
     * @param event       the render stage event
     * @param type        the selected goo type
     * @param partialTick the partial tick captured at the opaque stage
     */
    private static void renderTouchRing(RenderLevelStageEvent.AfterTranslucentBlocks event,
                                        ResourceKey<GooTypeDefinition> type, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ArcRenderer.renderReachRing(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), mc.player.getPosition(partialTick),
                mc.player.entityInteractionRange(), ClientGooTypes.highlight(type), partialTick);
    }

    /**
     * The peak weight the aim line draws toward: 1 for a lob onto a top face
     * the selected delivery allows, 0 otherwise.
     *
     * @param target   the target this frame aims at
     * @param delivery the selected ability's delivery
     * @return the granny weight
     */
    private static double grannyWeight(TargetResult target, Delivery delivery) {
        return delivery.grannyAllowed() && target instanceof TargetResult.BlockTarget bt && bt.grannyArc() ? 1 : 0;
    }

    /**
     * The real-time clock the ripple runs on, so a slow or paused tick leaves
     * its timing unchanged (decision ripple-rings-fade-to-face-edge).
     *
     * @return seconds on the monotonic clock
     */
    private static double realTimeSeconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }

    /**
     * Drops the target the arc stage would draw.
     */
    private static void clearCachedArc() {
        cachedArcTarget = null;
        cachedArcType = null;
        cachedArcDelivery = null;
        cachedArcTouchesAtReach = false;
        cachedArcPartialTick = 0f;
    }
}
