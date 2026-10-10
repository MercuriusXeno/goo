package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ClientGooTypes;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.overlay.TickFaceOverlay;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;

/**
 * How a timekeeper prism draws: the crystal tinted aeon gold inside a shell
 * of Tick's golden marching squares, a clock face on every side. The shell
 * reads the prism's bank: its squares march faster and glow brighter the
 * more charge it holds, and race while Tick spends it.
 * timekeeper-prism-banks-ticks-forward-only
 */
public final class TimekeeperPrismStyle implements PrismComboStyle {

    /** The combo this style draws: aeon's prism ability. */
    public static final String COMBO = "goo:aeon_timekeeper";

    /** The shell's width in blocks, around the half-block crystal. */
    private static final double SHELL_SIZE = 0.75;
    private static final double HALF = 0.5;
    /** The pace the shell's squares march at while Tick spends the bank, as extra ticks. */
    static final int SPENDING_PACE = 24;
    /** The most pace a standing bank alone gives, short of the spending race. */
    static final int MOST_STANDING_PACE = 12;
    /** The charge each step of standing pace doubles, so a fresh prism stirs and a day's bank hums. */
    private static final double CHARGE_PER_PACE_STEP = 100.0;
    /** How bright an empty prism's shell glows, and the charge whose order of ten adds the rest. */
    static final float EMPTY_GLOW = 0.35f;
    private static final double FULL_GLOW_ORDERS = 5.0;
    private static final double LOG_OF_TWO = Math.log(2);

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look != null) {
            poseStack.pushPose();
            PrismCrystal.standOnLandingFace(poseStack, state.facing);
            CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, aeonGold(look),
                    state.lightCoords);
            poseStack.popPose();
        }
        int pace = state.bankSpending ? SPENDING_PACE : standingPace(state.bankTotal);
        float glow = state.bankSpending ? 1f : glow(state.bankTotal);
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.TICK_FACE_TYPE, (pose, consumer) -> {
            FlatQuadContext quads = new FlatQuadContext(pose, consumer);
            Vec3 middle = new Vec3(HALF, HALF, HALF);
            for (Direction face : Direction.values()) {
                Vec3 center = middle.add(face.getUnitVec3().scale(SHELL_SIZE * HALF));
                TickFaceOverlay.emitFaceQuad(quads, center, face, SHELL_SIZE, pace, glow);
            }
        });
    }

    /**
     * The quartz's look tinted aeon gold, its sprite and alpha kept.
     *
     * @param look the plain quartz look
     * @return the gold look
     */
    private static CrystalClusterSubmitter.Look aeonGold(CrystalClusterSubmitter.Look look) {
        return new CrystalClusterSubmitter.Look(look.uv(),
                ARGB.color(ARGB.alpha(look.color()), ClientGooTypes.color(GooTypes.AEON)));
    }

    /**
     * The pace a standing bank's squares march at: one step faster each time
     * the charge doubles past a hundred, up to the standing ceiling.
     *
     * @param charge the bank's charge
     * @return the pace, as extra ticks
     */
    static int standingPace(long charge) {
        int doublings = (int) Math.floor(Math.log(1 + Math.max(0L, charge) / CHARGE_PER_PACE_STEP) / LOG_OF_TWO);
        return Math.min(MOST_STANDING_PACE, doublings);
    }

    /**
     * How brightly the shell glows for a bank's charge: dim when empty,
     * whole by a hundred thousand.
     *
     * @param charge the bank's charge
     * @return 0 to 1
     */
    static float glow(long charge) {
        double orders = Math.log10(1 + Math.max(0L, charge));
        return (float) Math.min(1.0, EMPTY_GLOW + (1 - EMPTY_GLOW) * orders / FULL_GLOW_ORDERS);
    }
}
