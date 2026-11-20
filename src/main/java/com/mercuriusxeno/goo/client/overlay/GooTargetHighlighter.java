package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.BuildFootprint;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.block.ability.FaceSupport;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.TargetResult;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.ChargeMotes;
import com.mercuriusxeno.goo.client.ability.HeldDomeRenderer;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.throwing.BuildCast;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.client.throwing.GloveUseTracker;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * Draws the glove's aim each frame from the frame's {@link AimTracker}: a
 * goo-colored outline and face ripple on the targeted block or ability block
 * with the opaque blocks, with no fill over the block, and the held ghost once
 * the level is drawn. The entity outline rides the render state modifier
 * AimTracker registers (decision render-context-is-the-one-emitter).
 */
public final class GooTargetHighlighter {

    private static final double NANOS_PER_SECOND = 1e9;

    /**
     * The held ghost {@link #collectThroughBlocks} resolved this frame, which
     * {@link #collect} draws over blocks and clears.
     */
    private static @Nullable HeldShow shown;

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
     * Hands the shared path the targeted block's or ability block's outline,
     * water cube and bullseye rings, which lie on the block's faces, so they
     * sort by nothing. In a view through a gate it hands the marks of a
     * target the aim reached through that gate, seen from the view's camera,
     * so they show on the far side through the view.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision aim-and-shots-carry-through-linked-gates
     *
     * @param frame the frame being drawn
     * @param draws where the marks' draw goes
     */
    public static void collectOutline(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        boolean view = PortalView.drawing();
        ResourceKey<GooTypeDefinition> selectedType = markedType(mc, view);
        if (selectedType == null) {
            return;
        }
        TargetResult target = AimTracker.currentTarget();
        AimIndicator indicator = indicatorFor(target, GloveAim.selectedAbilityId(mc.player));
        HighlightFrame highlight = new HighlightFrame(frame.poseStack(), ImmediateBuffers.shared(),
                view ? PortalView.camera() : mc.gameRenderer.mainCamera(), mc, selectedType);
        draws.accept(new SortedDraw(0, () -> {
            renderIndicator(target, indicator, highlight);
            highlight.buf().endBatch();
        }));
    }

    // The goo type whose marks draw in the pass drawing now, or null: the main frame draws a near-side target's,
    // a view through a gate a target's the aim reached through that gate (aim-and-shots-carry-through-linked-gates).
    private static @Nullable ResourceKey<GooTypeDefinition> markedType(Minecraft mc, boolean view) {
        boolean drawn = mc.player != null && (view || mc.level != null) && mc.options.getCameraType().isFirstPerson();
        if (!drawn || !drawsMarks(view, PortalView.viewedGate(), AimTracker.currentTarget().passage())) {
            return null;
        }
        return GloveAim.selectedGooType(mc.player);
    }

    /**
     * Resolves the selected ability's held ghost for this frame and hands the
     * shared path each of its layers' through-blocks pass, sorted by the
     * aimed point. The frame's upkeep runs here rather than in
     * {@link #collect}: the path draws THROUGH_BLOCKS before FAR_TO_NEAR, so
     * this runs first each frame, queues a released charge's motes before
     * WispGlow hands its far-to-near draws, and leaves the ghost the
     * over-blocks pass then draws.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision held-visual-ghosts-the-landing-in-two-passes
     *
     * @param frame the frame being drawn
     * @param draws where the through-blocks pass's draw goes
     */
    public static void collectThroughBlocks(LevelFrame frame, Consumer<SortedDraw> draws) {
        if (PortalView.drawing()) {
            return;
        }
        HeldShow show = heldShow(frame);
        shown = show;
        if (show != null && show.ghost() != null) {
            HeldDomeRenderer.GhostAt ghost = show.ghost();
            HighlightFrame highlight = show.highlight();
            draws.accept(new SortedDraw(show.distanceSquared(), () -> HeldDomeRenderer.drawThroughBlocks(
                    highlight.ps(), highlight.buf(), highlight.camera().position(), ghost, show.nowSeconds())));
        }
    }

    /**
     * Hands the shared path the held ghost {@link #collectThroughBlocks}
     * resolved this frame: each layer's over-blocks pass and its rings, or a
     * Build's footprint (decision spire-rips-walls-and-platforms), sorted by
     * the aimed point. It draws once the level
     * is drawn, so a prism's crystal, a translucent feature writing depth,
     * draws under the ghost standing in front of it rather than over it.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision held-visual-ghosts-the-landing-in-two-passes
     *
     * @param frame the frame being drawn
     * @param draws where the over-blocks pass's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        HeldShow show = shown;
        if (PortalView.drawing() || show == null) {
            return;
        }
        shown = null;
        if (show.footprint().isPresent() || show.ghost() != null) {
            draws.accept(new SortedDraw(show.distanceSquared(), () -> drawOverBlocks(show)));
        }
    }

    /**
     * What the held ghost shows this frame, resolved once so its two passes
     * draw the same ghost on the same clock.
     *
     * @param highlight  what the frame draws with
     * @param aimed      the aimed point, null where nothing is aimed at
     * @param footprint  a Build's footprint, sized as the cast is made
     * @param ghost      the ghost, null where none draws
     * @param nowSeconds seconds on the real-time clock the rings run on
     */
    private record HeldShow(HighlightFrame highlight, @Nullable Vec3 aimed, Optional<BuildFootprint> footprint,
                            HeldDomeRenderer.@Nullable GhostAt ghost, double nowSeconds) {

        /**
         * The squared distance from the camera to the aimed point, where the ghost sorts.
         *
         * @return the distance, zero where nothing is aimed at
         */
        double distanceSquared() {
            return aimed == null ? 0 : aimed.distanceToSqr(highlight.camera().position());
        }
    }

    private static @Nullable HeldShow heldShow(LevelFrame frame) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.options.getCameraType().isFirstPerson()) {
            return null;
        }
        ResourceKey<GooTypeDefinition> selectedType = GloveAim.selectedGooType(mc.player);
        if (selectedType == null) {
            return null;
        }
        HighlightFrame highlight = new HighlightFrame(frame.poseStack(), ImmediateBuffers.shared(),
                mc.gameRenderer.mainCamera(), mc, selectedType);
        TargetResult target = AimTracker.currentTarget();
        Optional<BuildFootprint> footprint = BuildCast.footprint();
        HeldDomeRenderer.GhostAt ghost = footprint.isPresent() ? null
                : heldGhost(target, GloveAim.selectedAbilityId(mc.player), highlight);
        return new HeldShow(highlight, target.point(), footprint, ghost, realTimeSeconds());
    }

    /**
     * The ghost stone Build's preview draws while the rise is being set: the
     * cells the planned growth fills, so a column the plan fizzles shows none.
     * decision column-meeting-a-block-entity-fizzles-at-no-cost
     *
     * @return the ghost cells, empty outside the rise or where nothing grows
     */
    private static List<BlockPos> ghostStone() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !BuildCast.rising()) {
            return List.of();
        }
        return BuildCast.plannedGrowth(player).map(BuildPreview::ghostCells).orElse(List.of());
    }

    private static void drawOverBlocks(HeldShow show) {
        HighlightFrame frame = show.highlight();
        int ringRgb = ClientGooTypes.highlight(frame.selectedType());
        show.footprint().ifPresent(footprint -> BuildPreviewRenderer.render(frame.ps(), frame.buf(),
                frame.camera().position(), footprint, ghostStone(), ringRgb, show.nowSeconds()));
        HeldDomeRenderer.GhostAt ghost = show.ghost();
        if (ghost != null) {
            HeldDomeRenderer.drawOverBlocks(frame.ps(), frame.buf(), frame.camera().position(), ghost, ringRgb,
                    show.nowSeconds());
        }
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
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        // dart-forms-on-right-click-and-throws-on-left-click: a held javelin marks the mob it aims at
        AimIndicator badged = AimIndicator.of(ability == null ? null
                : ability.badge().aimsAs(ability.delivery().kind()), AimTracker.grabHoldsAMob());
        boolean growsAPrism = ability != null && PrismBlock.grownBy(ability.behaviors());
        Level level = Minecraft.getInstance().level;
        boolean growable = target instanceof TargetResult.BlockTarget bt && level != null
                && FaceSupport.growsFrom(level, bt.pos(), bt.face());
        return AimIndicator.onFace(badged, growsAPrism, growable);
    }

    /**
     * The selected ability's held ghost at the aim point while right click
     * holds an armed press, or a sized cast's ghost at the spot its charge
     * rooted at.
     * held-visual-ghosts-the-landing-in-two-passes
     *
     * @param target    the aim target
     * @param abilityId the selected ability id
     * @param frame     what the frame draws with
     * @return the ghost, or null where none draws
     */
    private static HeldDomeRenderer.@Nullable GhostAt heldGhost(TargetResult target, @Nullable String abilityId,
                                                                HighlightFrame frame) {
        if (GloveThrowSender.selectedSizesByCharge(abilityId)) {
            return sizedGhost(frame);
        }
        ClientAbility ability = abilityId == null ? null : AbilitySyncHandler.findAbility(abilityId);
        if (ability == null || !HeldDomeRenderer.showsDome(ability.delivery(), ability.badge(), ability.area(),
                GloveUseTracker.showsArea())) {
            return null;
        }
        return HeldDomeRenderer.ghostAt(target, ability, frame.selectedType());
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
     * A sized ability's ghost at the spot its charge rooted at, at the size
     * its charge has reached, following the charge as right click holds
     * (decision spontaneous-abilities-cast-at-the-target-with-no-thrown-blob).
     * A released charge's motes queue here, once a frame, for WispGlow to draw.
     *
     * @param frame what the frame draws with
     * @return the ghost, or null where no charge stands
     */
    private static HeldDomeRenderer.@Nullable GhostAt sizedGhost(HighlightFrame frame) {
        float partialTick = frame.mc().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // blast-eases-between-stages-at-a-second-each: the spheroid eases while the cast keeps its whole stage
        OptionalDouble size = GloveThrowSender.drawnChargeRadius(frame.mc().player, partialTick);
        if (frame.mc().level == null) {
            return null;
        }
        double now = frame.mc().level.getGameTime() + partialTick;
        // a released charge's motes still draw as they are pushed out and fade
        ChargeMotes.render(now);
        if (size.isEmpty()) {
            return null;
        }
        // blast-charge-pulls-green-motes-into-the-shell-and-the-blob: the spheroid swells on the palm blob's beat
        float swelled = (float) (size.getAsDouble() * ChargeMotes.spheroidSwell(now));
        // blast-roots-to-the-spot-the-cast-began: the spheroid draws where the charge began, not where the aim is
        return HeldDomeRenderer.sizedGhostAt(GloveThrowSender.chargeAnchor(), swelled, frame.selectedType());
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
    private record HighlightFrame(PoseStack ps, ImmediateBuffers buf, Camera camera,
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
