package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * A glacial prism's look: the quartz column tinted the pale blue of ice;
 * the frost rings it pulses out to its reach mark it as the one holding its
 * ground frozen (decision glacial-prism-holds-the-area-frozen).
 */
public final class GlacialPrismStyle implements PrismComboStyle {

    /** The combo id a glacial prism carries, frost's prism ability. */
    public static final String COMBO = "goo:frost_glacial";

    /** The pale ice blue the column takes over its quartz. */
    private static final int ICE_TINT = 0xC4ECFF;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look == null) {
            return;
        }
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS,
                new CrystalClusterSubmitter.Look(look.uv(), iced(look.color())), state.lightCoords);
        poseStack.popPose();
    }

    /**
     * The column's color tinted toward ice, keeping its alpha.
     *
     * @param color the plain column's ARGB color
     * @return the iced ARGB color
     */
    static int iced(int color) {
        return ARGB.multiply(color, ARGB.opaque(ICE_TINT));
    }
}
