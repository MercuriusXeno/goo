package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.PrismColumn;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * The column of a prism that took one of glow's combos: the prism's quartz
 * re-tinted glow-yellow and drawn full-bright, inside a faint halo shaped
 * like the column itself, the column again swollen a little and drawn
 * faintly, so the prism reads as glow's whichever combo it holds; its beams
 * tell the beacon from the reflector (operator rulings 2026-10-09), and the
 * reflector's column folds into four sides as its combo takes. The column
 * and its halo write no depth, so a beam drawn after them, Sunbeam's ray
 * refracting off a reflector among them, shows in front of the column.
 * decisions bulb-one-model-max-light-beacon-combo, reflector-rails-carry-the-brightest-light
 * relay-and-metronome-read-apart-at-rest
 * decision diagnose-and-correct-the-sunbeam-prism-depth
 */
final class GlowColumn {

    /** The column's tint: glow-yellow, as translucent as the plain crystal. */
    static final int TINT = ARGB.color(0xE0, 0xFFE628);
    /** The halo's color: a faint glow-yellow. */
    static final int HALO = ARGB.color(56, 0xFFD700);
    /** How much wider the halo stands than the column. */
    static final float HALO_WIDTH = 1.3f;
    /** How much longer the halo stands than the column. */
    static final float HALO_LENGTH = 1.12f;
    /**
     * The submit order a glow beam draws in. Translucent geometry in one order
     * draws per render type in no fixed order, so a beam sharing the column's
     * order 0 could draw first and the column cover it; the later order draws
     * every beam after every column.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     */
    static final int BEAM_ORDER = 1;
    private static final double PIXEL = 1.0 / 16.0;
    /** The column's base point in the stood pose, about which the halo swells. */
    private static final Vec3 COLUMN_BASE = new Vec3(PrismColumn.BASE_X * PIXEL, PrismColumn.BASE_Y * PIXEL,
            PrismColumn.BASE_Z * PIXEL);

    private GlowColumn() {
    }

    /**
     * Draws the glow column and its halo.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     * @param sides         the column's resting sides
     */
    static void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector,
                       PrismCrystal.ColumnSides sides) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look == null) {
            return;
        }
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        int light = GooSubmitter.fullbrightLight();
        double fold = OculusStyle.transformationShare(state.gameTime, state.comboSince);
        PrismCrystal.submitColumn(poseStack, nodeCollector, GooRenderTypes.GLOW_COLUMN_TYPE, sides, fold,
                new CrystalClusterSubmitter.Look(look.uv(), TINT), light);
        poseStack.translate(COLUMN_BASE.x, COLUMN_BASE.y, COLUMN_BASE.z);
        poseStack.scale(HALO_WIDTH, HALO_LENGTH, HALO_WIDTH);
        poseStack.translate(-COLUMN_BASE.x, -COLUMN_BASE.y, -COLUMN_BASE.z);
        PrismCrystal.submitColumn(poseStack, nodeCollector, GooRenderTypes.GLOW_COLUMN_TYPE, sides, fold,
                new CrystalClusterSubmitter.Look(look.uv(), HALO), light);
        poseStack.popPose();
    }
}
