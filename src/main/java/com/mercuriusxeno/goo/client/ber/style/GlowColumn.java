package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GlowQueue;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * The column of a prism that took one of glow's combos: the prism's quartz
 * re-tinted glow-yellow and drawn full-bright, inside a faint halo shaped
 * like the column itself, the column again swollen a little and drawn
 * faintly, so the prism reads as glow's whichever combo it holds; its beams
 * tell the beacon from the reflector (operator rulings 2026-10-09), and the
 * reflector's column folds into four sides as its combo takes. The column
 * and its halo write no depth, so a beam drawn after them, Laser's ray
 * refracting off a reflector among them, shows in front of the column.
 * Drawn in vanilla's main pass, before the clouds, they took the paint of a
 * farther cloud, so the renderer queues them and the shared path draws them
 * far to near after the clouds (operator ruling 2026-11-18).
 * decisions bulb-one-model-max-light-beacon-combo, reflector-rails-carry-the-brightest-light
 * relay-and-metronome-read-apart-at-rest
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
public final class GlowColumn {

    /** The column's tint: glow-yellow, as translucent as the plain crystal. */
    static final int TINT = ARGB.color(0xE0, 0xFFE628);
    /** The halo's color: a faint glow-yellow. */
    static final int HALO = ARGB.color(56, 0xFFD700);
    /** How much wider the halo stands than the column. */
    static final float HALO_WIDTH = 1.3f;
    /** How much longer the halo stands than the column. */
    static final float HALO_LENGTH = 1.12f;
    private static final double PIXEL = 1.0 / 16.0;
    /** The column's base point in the stood pose, about which the halo swells. */
    private static final Vec3 COLUMN_BASE = new Vec3(PrismColumn.BASE_X * PIXEL, PrismColumn.BASE_Y * PIXEL,
            PrismColumn.BASE_Z * PIXEL);

    private static final GlowQueue QUEUED = new GlowQueue();

    private GlowColumn() {
    }

    /**
     * Queues the glow column and its halo for the shared path as one draw at
     * the prism's center, both in one batch as they drew in the main pass, so
     * the render type's upload sort orders their faces together.
     *
     * @param state     the prism's render state
     * @param poseStack the pose at the prism's cell corner, camera-relative
     * @param sides     the column's resting sides
     */
    static void queue(PrismRenderState state, PoseStack poseStack, PrismCrystal.ColumnSides sides) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look == null) {
            return;
        }
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        PoseStack.Pose columnPose = poseStack.last().copy();
        poseStack.translate(COLUMN_BASE.x, COLUMN_BASE.y, COLUMN_BASE.z);
        poseStack.scale(HALO_WIDTH, HALO_LENGTH, HALO_WIDTH);
        poseStack.translate(-COLUMN_BASE.x, -COLUMN_BASE.y, -COLUMN_BASE.z);
        PoseStack.Pose haloPose = poseStack.last().copy();
        poseStack.popPose();
        int light = GooSubmitter.fullbrightLight();
        double fold = OculusStyle.transformationShare(state.gameTime, state.comboSince);
        CrystalClusterSubmitter.Look column = new CrystalClusterSubmitter.Look(look.uv(), TINT);
        CrystalClusterSubmitter.Look halo = new CrystalClusterSubmitter.Look(look.uv(), HALO);
        QUEUED.queue(columnPose, Vec3.atCenterOf(state.blockPos), GooRenderTypes.GLOW_COLUMN_TYPE, (pose, consumer) -> {
            PrismCrystal.emitColumn(pose, consumer, sides, fold, column, light);
            PrismCrystal.emitColumn(haloPose, consumer, sides, fold, halo, light);
        });
    }

    /**
     * Hands the shared path one draw for each glow column and its halo the
     * prisms queued this frame, each sorted by the camera's distance to its
     * prism's center, then empties the queue.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     *
     * @param frame the frame being drawn
     * @param draws where each column's and halo's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        QUEUED.drain(frame, draws);
    }
}
