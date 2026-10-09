package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.overlay.TickFaceOverlay;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * How a timekeeper prism draws: the plain crystal inside a shell of Tick's
 * golden marching squares, a clock face on every side, so a prism that
 * banks ticks reads as the aeon one.
 * timekeeper-prism-banks-ticks-forward-only
 */
public final class TimekeeperPrismStyle implements PrismComboStyle {

    /** The combo this style draws: aeon's prism ability. */
    public static final String COMBO = "goo:aeon_timekeeper";

    /** The shell's width in blocks, around the half-block crystal. */
    private static final double SHELL_SIZE = 0.75;
    private static final double HALF = 0.5;
    /** The pace the shell's squares march at, a slow standing tick. */
    private static final int STANDING_PACE = 0;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look != null) {
            poseStack.pushPose();
            PrismCrystal.standOnLandingFace(poseStack, state.facing);
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, look, state.lightCoords);
            poseStack.popPose();
        }
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.TICK_FACE_TYPE, (pose, consumer) -> {
            FlatQuadContext quads = new FlatQuadContext(pose, consumer);
            Vec3 middle = new Vec3(HALF, HALF, HALF);
            for (Direction face : Direction.values()) {
                Vec3 center = middle.add(face.getUnitVec3().scale(SHELL_SIZE * HALF));
                TickFaceOverlay.emitFaceQuad(quads, center, face, SHELL_SIZE, STANDING_PACE);
            }
        });
    }
}
