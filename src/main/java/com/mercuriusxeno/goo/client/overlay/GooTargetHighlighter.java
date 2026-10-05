package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooClientConfig;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
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
    /** The target the drawn arc eases toward, or null when no arc is drawn. */
    private static @Nullable TargetResult easedTarget;
    /** The endpoint drawn when the target last changed, or null when none was drawn. */
    private static @Nullable Vec3 easeFromEndpoint;
    /** The granny weight drawn when the target last changed. */
    private static double easeFromGrannyWeight;
    /** Real-time seconds when the target last changed. */
    private static double easeStartSeconds;
    /** The endpoint drawn last frame, or null when none was drawn. */
    private static @Nullable Vec3 drawnEndpoint;
    /** The granny weight drawn last frame. */
    private static double drawnGrannyWeight;

    private GooTargetHighlighter() {
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
            clearEasedArc();
            return;
        }
        if (touchesAtReach) {
            renderTouchRing(event, type, partialTick);
        }
        renderAimLine(event, target, type, delivery, partialTick);
    }

    /**
     * Draws the eased aim line toward the target, or clears the ease when the
     * target resolves no endpoint.
     *
     * @param event       the render stage event
     * @param target      the target this frame aims at
     * @param type        the selected goo type
     * @param delivery    the selected delivery, an arc or a beam
     * @param partialTick the partial tick captured at the opaque stage
     */
    private static void renderAimLine(RenderLevelStageEvent.AfterTranslucentBlocks event, TargetResult target,
                                      ResourceKey<GooTypeDefinition> type, Delivery delivery, float partialTick) {
        Vec3 end = target.resolveEndpoint();
        if (end == null) {
            clearEasedArc();
            return;
        }
        Vec3 drawn = easeArcToward(target, end, grannyWeight(target, delivery), realTimeSeconds());
        Minecraft mc = Minecraft.getInstance();
        ArcRenderer.renderTargetArc(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), drawn, ClientGooTypes.highlight(type),
                partialTick, drawnGrannyWeight, delivery.fliesStraight());
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
     * Advances the drawn arc toward the target, restarting the ease from what
     * was drawn last frame whenever the target changes (decision
     * aim-line-lerps-toward-target).
     *
     * @param target       the target this frame aims at
     * @param end          the target's endpoint
     * @param grannyWeight the target's peak weight, 1 for a granny arc
     * @param nowSeconds   the real-time clock
     * @return the endpoint to draw this frame
     */
    private static Vec3 easeArcToward(TargetResult target, Vec3 end, double grannyWeight, double nowSeconds) {
        if (!target.equals(easedTarget)) {
            easedTarget = target;
            easeFromEndpoint = drawnEndpoint;
            easeFromGrannyWeight = drawnEndpoint == null ? grannyWeight : drawnGrannyWeight;
            easeStartSeconds = nowSeconds;
        }
        double elapsed = nowSeconds - easeStartSeconds;
        // decision aim-arc-snap-option
        double easeSeconds = GooClientConfig.SNAP_AIM_ARC.get() ? 0 : ArcEndpointEase.EASE_SECONDS;
        Vec3 drawn = ArcEndpointEase.easeEndpoint(easeFromEndpoint, end, elapsed, easeSeconds);
        drawnEndpoint = drawn;
        drawnGrannyWeight = ArcEndpointEase.easeGrannyWeight(easeFromGrannyWeight, grannyWeight, elapsed,
                easeSeconds);
        return drawn;
    }

    /**
     * The real-time clock the slide and the ripple run on, so a slow or paused
     * tick leaves their timing unchanged (decisions aim-arc-slides-in-real-time,
     * ripple-rings-fade-to-face-edge).
     *
     * @return seconds on the monotonic clock
     */
    private static double realTimeSeconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }

    /**
     * Forgets the drawn arc, so the next target draws at its own endpoint
     * rather than sliding in from a stale one.
     */
    private static void clearEasedArc() {
        easedTarget = null;
        easeFromEndpoint = null;
        easeFromGrannyWeight = 0;
        easeStartSeconds = 0;
        drawnEndpoint = null;
        drawnGrannyWeight = 0;
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
