package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.ability.program.HeaterStep;
import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * A heater prism's look: the quartz column tinted a deep ember orange-red and
 * drawn at full brightness, so it glows like a hot coal in the dark. The
 * operator's ruling (idea heater-prism-rulings): ember tint and glow.
 * heater-prism-flames-nearby-creatures
 */
public final class HeaterPrismStyle implements PrismComboStyle {

    /**
     * The combo id a heater prism carries: Flames, whose on_prism reaction is the heater.
     * channeled-hosts-convert-prisms-over-the-hold
     */
    public static final String COMBO = "goo:blaze_flames";

    /** The deep ember orange-red the column takes over its quartz. */
    static final int EMBER = HeaterStep.EMBER;
    /** Where the block light sits in packed light coordinates. */
    private static final int BLOCK_SHIFT = 4;
    /** Where the sky light sits in packed light coordinates. */
    private static final int SKY_SHIFT = 20;
    /** The brightest light level, and the mask of one level in packed light. */
    private static final int LIGHT_MASK = 15;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look look = state.look;
        if (look == null) {
            return;
        }
        poseStack.pushPose();
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS,
                new CrystalClusterSubmitter.Look(look.uv(), embered(look.color())), GooSubmitter.fullbrightLight());
        poseStack.popPose();
    }

    /**
     * The column's color tinted deep ember, keeping its alpha.
     *
     * @param color the plain column's ARGB color
     * @return the embered ARGB color
     */
    static int embered(int color) {
        return ARGB.multiply(color, ARGB.opaque(EMBER));
    }

    /**
     * Whether a charging prism's tint is the heater's, so its glow rises with
     * its ember as Flames charges it.
     * prism-conversion-creeps-over-the-hold
     *
     * @param tint the RGB the charge warms the prism toward
     * @return true for the heater's ember
     */
    public static boolean glowsWhileCharging(int tint) {
        return tint == EMBER;
    }

    /**
     * The light a charging heater draws by: its block and sky light each
     * raised toward full by the share charged, so the last tick of the hold
     * draws as bright as the heater it becomes.
     * prism-conversion-creeps-over-the-hold
     *
     * @param light the packed light the prism stands in
     * @param share how far Flames has charged the prism, 0 to 1
     * @return the packed light, full bright at a full share
     */
    public static int warmingLight(int light, float share) {
        float warmth = Math.clamp(share, 0f, 1f);
        int block = raised((light >> BLOCK_SHIFT) & LIGHT_MASK, warmth);
        int sky = raised((light >> SKY_SHIFT) & LIGHT_MASK, warmth);
        return block << BLOCK_SHIFT | sky << SKY_SHIFT;
    }

    private static int raised(int level, float warmth) {
        return Math.round(level + (LIGHT_MASK - level) * warmth);
    }
}
