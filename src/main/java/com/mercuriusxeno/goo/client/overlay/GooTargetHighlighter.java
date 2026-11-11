package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.GateRay;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.HeldColumnRenderer;
import com.mercuriusxeno.goo.client.ability.HeldDomeRenderer;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.ability.SculkerVision;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.SpireCast;
import com.mercuriusxeno.goo.throwing.GateArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the glove's aim each frame from the frame's {@link AimTracker}: a
 * goo-colored outline and face ripple on the targeted block or ability block
 * at the opaque stage, with no fill over the block, and the throw arc, or a
 * line per knife for a delivery slinging a fan, after translucent blocks. The entity outline
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
    /** Whether the selected ability is drag-sized, so the arc stage draws no throw arc for it. */
    private static boolean cachedArcDragSized;
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
        if (hidesVanillaCrosshair(reticuleDrawn, SculkerVision.presence())
                && VanillaGuiLayers.CROSSHAIR.equals(event.getName())) {
            event.setCanceled(true);
        }
    }

    /**
     * Whether the reticule stands in for the vanilla crosshair this frame:
     * only where it was drawn and shows, never while Sculker's senses dim the
     * world, which blacks out the reticule drawn into it and would leave no
     * crosshair at all.
     *
     * @param reticuleDrawn   whether the frame drew the reticule
     * @param sculkerPresence how fully Sculker's senses stand, 0 to 1
     * @return true when the vanilla crosshair is hidden
     */
    static boolean hidesVanillaCrosshair(boolean reticuleDrawn, float sculkerPresence) {
        return reticuleDrawn && sculkerPresence < SculkerVision.GONE;
    }

    /**
     * Renders the targeted block or ability block highlight and leaves the
     * target for the arc stage.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (PortalView.drawing()) {
            drawMarksThroughTheGate(event);
            return;
        }
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
        cachedArcDragSized = GloveThrowSender.selectedDragSized(abilityId);
        cachedArcPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        HighlightFrame frame = new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), mc, selectedType);
        if (drawsMarks(false, null, target.passage())) {
            renderIndicator(target, AimIndicator.of(GloveThrowSender.selectedBadge(abilityId),
                    AimTracker.grabHoldsAMob()), frame);
        }
        renderHeldDome(target, abilityId, frame);
    }

    /**
     * In a view through a gate, draws the marks of a target the aim reached
     * through that gate, seen from the view's camera, so the outline and the
     * ripple show on the far side through the view; the line and the held
     * ghost stay the frame's own.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param event the view pass's render stage event
     */
    private static void drawMarksThroughTheGate(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        TargetResult target = AimTracker.currentTarget();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()
                || !drawsMarks(true, PortalView.viewedGate(), target.passage())) {
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(mc.player);
        if (selectedType == null) {
            return;
        }
        String abilityId = GloveAim.selectedAbilityId(mc.player);
        renderIndicator(target, AimIndicator.of(GloveThrowSender.selectedBadge(abilityId), AimTracker.grabHoldsAMob()),
                new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(), PortalView.camera(), mc,
                        selectedType));
    }

    /**
     * Draws the selected ability's held ghost at the aim point while right
     * click holds an armed press.
     * held-visual-ghosts-the-landing-in-two-passes
     *
     * @param target    the aim target
     * @param abilityId the selected ability id
     * @param frame     what the frame draws with
     */
    private static void renderHeldDome(TargetResult target, @Nullable String abilityId, HighlightFrame frame) {
        if (renderSizedPreview(abilityId, frame)) {
            return;
        }
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        if (ability == null || renderHeldColumn(target, ability, frame)
                || !HeldDomeRenderer.showsDome(ability.delivery(), ability.badge(), ability.area(),
                GloveUseTracker.showsArea())) {
            return;
        }
        HeldDomeRenderer.render(frame.ps(), frame.buf(), frame.camera().position(), target, ability,
                frame.selectedType(), ClientGooTypes.highlight(frame.selectedType()), realTimeSeconds());
    }

    /**
     * Whether the aim target's marks draw in the pass drawing now.
     *
     * @param viewDrawing whether a view through a gate is drawing, rather than the main frame
     * @param viewGate    the gate the drawing view looks into, null in the main frame
     * @param passage     the gate the aim passed to reach its target, null for a near-side target
     * @return true where the marks draw in this pass
     */
    static boolean drawsMarks(boolean viewDrawing, @Nullable Gate viewGate, TargetResult.@Nullable Passage passage) {
        // aim-and-shots-carry-through-linked-gates: a far-side target is seen through the gate the aim passed
        return passage == null ? !viewDrawing : viewDrawing && passage.gate().equals(viewGate);
    }

    /**
     * Draws the preview of a cast sized as it is made: a Spire's footprint
     * (decision spire-rips-walls-and-platforms) or a drag-sized ability's
     * dome (decision black-hole-leaves-a-compression-sphere).
     *
     * @param abilityId the selected ability id
     * @param frame     what the frame draws with
     * @return true where a sized cast drew its preview in place of the held ghost
     */
    private static boolean renderSizedPreview(@Nullable String abilityId, HighlightFrame frame) {
        Optional<SpireFootprint> footprint = SpireCast.footprint();
        if (footprint.isPresent()) {
            SpirePreviewRenderer.render(frame.ps(), frame.buf(), frame.camera().position(), footprint.get(),
                    SpireCast.rising(), ClientGooTypes.highlight(frame.selectedType()), realTimeSeconds());
            return true;
        }
        if (GloveThrowSender.selectedDragSized(abilityId)) {
            renderSizedDome(frame);
            return true;
        }
        return false;
    }

    /**
     * Draws a column ability's held column at the aim point while right click
     * holds an armed press.
     * updraft-blob-stands-a-column-of-wind
     *
     * @param target  the aim target
     * @param ability the selected ability
     * @param frame   what the frame draws with
     * @return true when the ability previews a column, which then drew
     */
    private static boolean renderHeldColumn(TargetResult target, ClientAbility ability, HighlightFrame frame) {
        if (!HeldColumnRenderer.showsColumn(ability.delivery(), ability.badge(), ability.area(),
                GloveUseTracker.showsArea())) {
            return false;
        }
        HeldColumnRenderer.render(frame.ps(), frame.buf(), frame.camera().position(), target, ability.area(),
                ClientGooTypes.highlight(frame.selectedType()));
        return true;
    }

    /**
     * Draws a sized ability's ghost at its pin while right click drags its
     * radius (decision black-hole-leaves-a-compression-sphere).
     *
     * @param frame what the frame draws with
     */
    private static void renderSizedDome(HighlightFrame frame) {
        BlockHitResult pin = GloveUseTracker.pressPin();
        OptionalDouble radius = GloveThrowSender.dragRadius(frame.mc().player);
        if (pin == null || radius.isEmpty()) {
            return;
        }
        HeldDomeRenderer.renderSized(frame.ps(), frame.buf(), frame.camera().position(), pin,
                (float) radius.getAsDouble(), frame.selectedType(), ClientGooTypes.highlight(frame.selectedType()),
                realTimeSeconds());
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
        if (PortalView.drawing()) {
            drawFarLegThroughTheGate(event);
            return;
        }
        TargetResult target = cachedArcTarget;
        ResourceKey<GooTypeDefinition> type = cachedArcType;
        Delivery delivery = cachedArcDelivery;
        float partialTick = cachedArcPartialTick;
        boolean touchesAtReach = cachedArcTouchesAtReach;
        boolean dragSized = cachedArcDragSized;
        clearCachedArc();
        if (target == null || type == null || delivery == null) {
            return;
        }
        if (KnifeAimLineRenderer.showsLines(delivery, GloveUseTracker.showsArea())) {
            Minecraft mc = Minecraft.getInstance();
            KnifeAimLineRenderer.render(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                    mc.gameRenderer.getMainCamera(), delivery, ClientGooTypes.highlight(type), partialTick);
            return;
        }
        if (drawsAimLine(delivery, dragSized)) {
            renderLineAim(event, target, type, delivery, partialTick, touchesAtReach);
        }
    }

    /**
     * Draws a line aim: the touch ring where the ability touches at reach,
     * and the aim line for a delivery aiming one.
     *
     * @param event          the render stage event
     * @param target         the target this frame aims at
     * @param type           the selected goo type
     * @param delivery       the selected delivery
     * @param partialTick    the partial tick captured at the opaque stage
     * @param touchesAtReach whether the selected ability touches an entity within reach
     */
    private static void renderLineAim(RenderLevelStageEvent.AfterTranslucentBlocks event, TargetResult target,
                                      ResourceKey<GooTypeDefinition> type, Delivery delivery, float partialTick,
                                      boolean touchesAtReach) {
        if (touchesAtReach) {
            renderTouchRing(event, type, partialTick);
        }
        renderAimLine(event, target, type, delivery, partialTick);
    }

    /**
     * Draws the aim line to its end for the target, the same frame the tile
     * highlight draws, with nothing carried from the last frame.
     * aim-line-snaps-with-the-tile-highlight
     * aim-line-roots-to-the-aimed-mob-or-block
     *
     * @param event       the render stage event
     * @param target      the target this frame aims at
     * @param type        the selected goo type
     * @param delivery    the selected delivery, an arc or a beam
     * @param partialTick the partial tick captured at the opaque stage
     */
    private static void renderAimLine(RenderLevelStageEvent.AfterTranslucentBlocks event, TargetResult target,
                                      ResourceKey<GooTypeDefinition> type, Delivery delivery, float partialTick) {
        Vec3 end = aimLineEnd(target);
        if (end == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Optional<GateArc.Hop> hop = hopOf(target);
        if (hop.isPresent()) {
            // aim-and-shots-carry-through-linked-gates: the frame draws the near leg, ending on the gate's face
            Camera camera = mc.gameRenderer.getMainCamera();
            ArcRenderer.renderGateLeg(event.getPoseStack(), mc.renderBuffers().bufferSource(), camera,
                    GloveAim.handPosition(camera), hop.get(), end, false,
                    new ArcRenderer.LineStyle(ClientGooTypes.highlight(type), partialTick,
                            grannyWeight(target, delivery), delivery.aimsStraight()));
            return;
        }
        ArcRenderer.renderTargetArc(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), end, ClientGooTypes.highlight(type),
                partialTick, grannyWeight(target, delivery), delivery.aimsStraight());
    }

    /**
     * The hop through a gate the aim line makes to reach a target, empty for
     * a target on the near side.
     *
     * @param target the target this frame aims at
     * @return the hop
     */
    private static Optional<GateArc.Hop> hopOf(TargetResult target) {
        TargetResult.Passage passage = target.passage();
        return passage == null ? Optional.empty()
                : GateRay.hopThrough(GatePassage.clientPairs(), passage.gate(), passage.facePoint());
    }

    /**
     * In a view through a gate, draws the aim line's far leg, from the
     * partner to the target, where the aim passed that gate, so the line is
     * seen carrying on through it.
     * aim-and-shots-carry-through-linked-gates
     *
     * @param event the view pass's render stage event
     */
    private static void drawFarLegThroughTheGate(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        TargetResult target = AimTracker.currentTarget();
        Optional<GateArc.Hop> hop = hopOf(target);
        Vec3 end = aimLineEnd(target);
        boolean seenHere = drawsMarks(true, PortalView.viewedGate(), target.passage());
        if (mc.player == null || hop.isEmpty() || end == null || !seenHere) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = GloveAim.selectedGooType(mc.player);
        String abilityId = GloveAim.selectedAbilityId(mc.player);
        Delivery delivery = GloveThrowSender.selectedDelivery(abilityId);
        if (type == null || delivery == null || !drawsAimLine(delivery, GloveThrowSender.selectedDragSized(abilityId))) {
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson()
                || KnifeAimLineRenderer.showsLines(delivery, GloveUseTracker.showsArea())) {
            return;
        }
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ArcRenderer.renderGateLeg(event.getPoseStack(), mc.renderBuffers().bufferSource(), PortalView.camera(),
                GloveAim.handPosition(mc.gameRenderer.getMainCamera()), hop.get(), end, true,
                new ArcRenderer.LineStyle(ClientGooTypes.highlight(type), partialTick,
                        grannyWeight(target, delivery), delivery.aimsStraight()));
    }

    /**
     * Whether the held glove draws the throw arc for the selected ability: a
     * beam or a one-blob arc that is thrown, never a drag-sized ability,
     * which opens where it is pinned and draws its drag ring alone.
     * blast-draws-no-throw-arc
     *
     * @param delivery  the selected ability's delivery
     * @param dragSized whether the selected ability is drag-sized
     * @return true where the aim line draws
     */
    static boolean drawsAimLine(Delivery delivery, boolean dragSized) {
        return delivery.aimsALine() && !dragSized;
    }

    /**
     * Where the aim line ends for a target: the bullseye's anchor on an aimed
     * mob or block, so hand sway leaves the line rooted, and the cursor for
     * free aim, which favors nothing to root to.
     * aim-line-roots-to-the-aimed-mob-or-block
     *
     * @param target the target this frame aims at
     * @return the line's end, or null when nothing is aimed at
     */
    static @Nullable Vec3 aimLineEnd(TargetResult target) {
        return switch (target) {
            case TargetResult.PointTarget free -> free.point();
            case TargetResult.None none -> null;
            default -> target.resolveEndpoint();
        };
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
        cachedArcDragSized = false;
        cachedArcPartialTick = 0f;
    }
}
