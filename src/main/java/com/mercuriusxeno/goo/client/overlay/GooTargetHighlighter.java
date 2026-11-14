package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.HeldColumnRenderer;
import com.mercuriusxeno.goo.client.ability.HeldDomeRenderer;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.client.throwing.SpireCast;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws the glove's aim each frame from the frame's {@link AimTracker}: a
 * goo-colored outline and face ripple on the targeted block or ability block
 * at the opaque stage, with no fill over the block, and a line per knife for
 * a delivery slinging a fan after translucent blocks. The entity outline
 * rides the render state modifier AimTracker registers (decision
 * render-context-is-the-one-emitter).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {

    private static final double NANOS_PER_SECOND = 1e9;

    /**
     * Goo type the opaque stage leaves for the translucent line stage, or null
     * when the frame aimed none or the line stage already took it.
     */
    private static @Nullable ResourceKey<GooTypeDefinition> cachedLineType;
    /** Delivery of the selected ability for the line stage. */
    private static @Nullable Delivery cachedLineDelivery;
    /** Partial tick captured at the opaque stage. */
    private static float cachedLinePartialTick;

    private GooTargetHighlighter() {
    }

    /**
     * The line the aim pass draws: the knife lines while a fan-slinging press
     * is held, and no other, the vanilla crosshair aiming every other ability.
     * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring
     */
    enum AimPassLine {
        /** No line. */
        NONE,
        /** A line per knife the charge slings. */
        KNIFE_LINES
    }

    /**
     * The line the aim pass draws for the selected delivery.
     *
     * @param delivery the selected delivery
     * @param armed    whether right click holds an armed press
     * @return the line
     */
    static AimPassLine aimPassLine(@Nullable Delivery delivery, boolean armed) {
        return KnifeAimLineRenderer.showsLines(delivery, armed) ? AimPassLine.KNIFE_LINES : AimPassLine.NONE;
    }

    /**
     * Renders the targeted block or ability block highlight and leaves the
     * selected delivery for the line stage.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (PortalView.drawing()) {
            drawMarksThroughTheGate(event);
            return;
        }
        clearCachedLine();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(mc.player);
        if (selectedType == null) {
            return;
        }
        TargetResult target = AimTracker.currentTarget();
        String abilityId = GloveAim.selectedAbilityId(mc.player);
        cachedLineType = selectedType;
        cachedLineDelivery = GloveThrowSender.selectedDelivery(abilityId);
        cachedLinePartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
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
     * ripple show on the far side through the view; the knife lines and the
     * held ghost stay the frame's own.
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
        if (renderSizedPreview(target, abilityId, frame)) {
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
     * (decision spire-rips-walls-and-platforms) or a charge-sized ability's
     * dome (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
     *
     * @param target    the aim target
     * @param abilityId the selected ability id
     * @param frame     what the frame draws with
     * @return true where a sized cast drew its preview in place of the held ghost
     */
    private static boolean renderSizedPreview(TargetResult target, @Nullable String abilityId,
                                              HighlightFrame frame) {
        Optional<SpireFootprint> footprint = SpireCast.footprint();
        if (footprint.isPresent()) {
            SpirePreviewRenderer.render(frame.ps(), frame.buf(), frame.camera().position(), footprint.get(),
                    SpireCast.rising(), ClientGooTypes.highlight(frame.selectedType()), realTimeSeconds());
            return true;
        }
        if (GloveThrowSender.selectedSizesByCharge(abilityId)) {
            renderSizedDome(target, frame);
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
     * Draws a sized ability's ghost at the aimed target at the size its
     * charge has reached, following the charge as right click holds
     * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
     *
     * @param target the aim target
     * @param frame  what the frame draws with
     */
    private static void renderSizedDome(TargetResult target, HighlightFrame frame) {
        OptionalDouble size = GloveThrowSender.chargedSize(frame.mc().player);
        if (size.isEmpty()) {
            return;
        }
        HeldDomeRenderer.renderSized(frame.ps(), frame.buf(), frame.camera().position(), target,
                (float) size.getAsDouble(), frame.selectedType(), ClientGooTypes.highlight(frame.selectedType()),
                realTimeSeconds());
    }

    /**
     * Draws the mark the selected ability's badge chooses: the outline of the
     * block or crystal aimed at, or nothing, the vanilla crosshair aiming.
     * target-kind-configured-per-ability
     * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring
     *
     * @param target    the aim target
     * @param indicator the mark the badge chooses
     * @param frame     what the frame draws with
     */
    private static void renderIndicator(TargetResult target, AimIndicator indicator, HighlightFrame frame) {
        if (indicator != AimIndicator.NONE) {
            renderTargetHighlight(target, frame);
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
     * Draws the knife lines after translucent blocks, so the depth buffer
     * holds both opaque and water depth for correct sorting.
     * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (PortalView.drawing()) {
            return;
        }
        ResourceKey<GooTypeDefinition> type = cachedLineType;
        Delivery delivery = cachedLineDelivery;
        float partialTick = cachedLinePartialTick;
        clearCachedLine();
        if (type == null || delivery == null
                || aimPassLine(delivery, GloveUseTracker.showsArea()) != AimPassLine.KNIFE_LINES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        KnifeAimLineRenderer.render(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), delivery, ClientGooTypes.highlight(type), partialTick);
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
     * Drops what the line stage would draw.
     */
    private static void clearCachedLine() {
        cachedLineType = null;
        cachedLineDelivery = null;
        cachedLinePartialTick = 0f;
    }
}
