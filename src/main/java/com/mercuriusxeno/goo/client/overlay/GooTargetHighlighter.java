package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.SpireFootprint;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.block.ability.FaceSupport;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.ability.ChargeMotes;
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
 * at the opaque stage, with no fill over the block, and the held ghost after
 * translucent features. The entity outline rides the render state modifier
 * AimTracker registers (decision render-context-is-the-one-emitter).
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GooTargetHighlighter {

    private static final double NANOS_PER_SECOND = 1e9;

    private GooTargetHighlighter() {
    }

    /**
     * The line the aim pass draws: none, the vanilla crosshair aiming every
     * ability, Knives among them.
     * vanilla-crosshair-aims-everything-without-reticule-line-or-touch-ring
     * knives-draws-no-aim-lines-the-vanilla-crosshair-aims-it
     */
    enum AimPassLine {
        /** No line. */
        NONE
    }

    /**
     * The line the aim pass draws for the selected delivery.
     *
     * @param delivery the selected delivery
     * @param armed    whether right click holds an armed press
     * @return the line
     */
    static AimPassLine aimPassLine(@Nullable Delivery delivery, boolean armed) {
        return AimPassLine.NONE;
    }

    /**
     * Renders the targeted block or ability block highlight.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterOpaqueFeatures(RenderLevelStageEvent.AfterOpaqueFeatures event) {
        if (PortalView.drawing()) {
            drawMarksThroughTheGate(event);
            return;
        }
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
        HighlightFrame frame = new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                mc.gameRenderer.getMainCamera(), mc, selectedType);
        if (drawsMarks(false, null, target.passage())) {
            renderIndicator(target, indicatorFor(target, abilityId), frame);
        }
    }

    /**
     * Draws the selected ability's held ghost once the translucent features
     * have drawn, so a prism's crystal, a translucent feature writing depth,
     * draws under the ghost standing in front of it rather than over it.
     *
     * @param event the event instance
     */
    @SubscribeEvent
    public static void onAfterTranslucentFeatures(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        Minecraft mc = Minecraft.getInstance();
        if (PortalView.drawing() || mc.player == null || mc.level == null
                || !mc.options.getCameraType().isFirstPerson()) {
            return;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(mc.player);
        if (selectedType == null) {
            return;
        }
        renderHeldDome(AimTracker.currentTarget(), GloveAim.selectedAbilityId(mc.player),
                new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(),
                        mc.gameRenderer.getMainCamera(), mc, selectedType));
    }

    /**
     * In a view through a gate, draws the marks of a target the aim reached
     * through that gate, seen from the view's camera, so the outline and the
     * ripple show on the far side through the view; the held ghost stays the
     * frame's own.
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
        renderIndicator(target, indicatorFor(target, abilityId),
                new HighlightFrame(event.getPoseStack(), mc.renderBuffers().bufferSource(), PortalView.camera(), mc,
                        selectedType));
    }

    /**
     * The mark the selected ability draws on the aim target: its badge's,
     * dropped for an ability growing a prism where the aimed face grows none.
     * decision prism-highlight-draws-only-on-a-face-a-prism-grows-from
     *
     * @param target    the aim target
     * @param abilityId the selected ability id
     * @return the mark
     */
    private static AimIndicator indicatorFor(TargetResult target, @Nullable String abilityId) {
        AimIndicator badged = AimIndicator.of(GloveThrowSender.selectedBadge(abilityId), AimTracker.grabHoldsAMob());
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        boolean growsAPrism = ability != null && PrismBlock.grownBy(ability.behaviors());
        Level level = Minecraft.getInstance().level;
        boolean growable = target instanceof TargetResult.BlockTarget bt && level != null
                && FaceSupport.growsFrom(level, bt.pos(), bt.face());
        return AimIndicator.onFace(badged, growsAPrism, growable);
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
     * (decision spire-rips-walls-and-platforms) or a charge-sized ability's
     * dome (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
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
        if (GloveThrowSender.selectedSizesByCharge(abilityId)) {
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
     * Draws a sized ability's ghost at the spot its charge rooted at, at the
     * size its charge has reached, following the charge as right click holds
     * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
     *
     * @param frame what the frame draws with
     */
    private static void renderSizedDome(HighlightFrame frame) {
        float partialTick = frame.mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // blast-eases-between-stages-at-a-second-each: the spheroid eases while the cast keeps its whole stage
        OptionalDouble size = GloveThrowSender.drawnChargeRadius(frame.mc().player, partialTick);
        if (frame.mc().level == null) {
            return;
        }
        double now = frame.mc().level.getGameTime() + partialTick;
        // a released charge's motes still draw as they are pushed out and fade
        ChargeMotes.render(now);
        if (size.isEmpty()) {
            return;
        }
        // blast-charge-pulls-green-motes-into-the-shell-and-the-blob: the spheroid swells on the palm blob's beat
        float swelled = (float) (size.getAsDouble() * ChargeMotes.spheroidSwell(now));
        // blast-roots-to-the-spot-the-cast-began: the spheroid draws where the charge began, not where the aim is
        HeldDomeRenderer.renderSized(frame.ps(), frame.buf(), frame.camera().position(),
                GloveThrowSender.chargeAnchor(), swelled, frame.selectedType(),
                ClientGooTypes.highlight(frame.selectedType()), realTimeSeconds());
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
     * The real-time clock the ripple runs on, so a slow or paused tick leaves
     * its timing unchanged (decision ripple-rings-fade-to-face-edge).
     *
     * @return seconds on the monotonic clock
     */
    private static double realTimeSeconds() {
        return System.nanoTime() / NANOS_PER_SECOND;
    }
}
