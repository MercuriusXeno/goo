package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ability.ColorSphere;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * The column of a prism that took one of glow's combos: the prism's quartz
 * re-tinted glow-yellow and drawn full-bright, inside a faint additive halo
 * of glow light, so the prism reads as glow's whichever combo it holds; its
 * beams tell the beacon from the reflector (operator ruling 2026-10-09).
 * decisions bulb-one-model-max-light-beacon-combo, reflector-rails-carry-the-brightest-light
 */
final class GlowColumn {

    /** The column's tint: glow-yellow, as translucent as the plain crystal. */
    static final int TINT = ARGB.color(0xE0, 0xFFE628);
    /** The halo's color: a faint glow-yellow added onto the world. */
    static final int HALO = ARGB.color(22, 0xFFD700);
    private static final float HALO_RADIUS = 0.45f;
    private static final Vec3 CELL_CENTER = new Vec3(0.5, 0.5, 0.5);

    private GlowColumn() {
    }

    /**
     * Draws the glow column and its halo.
     *
     * @param state         the prism's render state
     * @param poseStack     the pose at the prism's cell corner
     * @param nodeCollector the submit collector
     */
    static void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look != null) {
            poseStack.pushPose();
            PrismCrystal.standOnLandingFace(poseStack, state.facing);
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS,
                    new CrystalClusterSubmitter.Look(look.uv(), TINT), GooSubmitter.fullbrightLight());
            poseStack.popPose();
        }
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.GLOW_SHELL_TYPE,
                (pose, consumer) -> ColorSphere.emit(pose, consumer, CELL_CENTER, HALO_RADIUS, HALO));
    }
}
