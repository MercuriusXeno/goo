package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.PrismCrystal;
import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * A verdant prism's look: the plain prism's pointed quartz column drawn
 * leaf-green, while the breeze it puffs on each pulse and the sparkles where
 * it greens come from the server.
 * verdant-prism-greens-blocks-slowly
 * prism-is-one-pointed-quartz-column
 */
public final class VerdantPrismStyle implements PrismComboStyle {

    /** The id of the ability whose program is the verdant combo. */
    public static final String COMBO = "goo:leaf_verdant";
    /** A leaf-green the milky crystal is multiplied by, opaque so the crystal keeps its own alpha. */
    static final int LEAF_GREEN = 0xFF8FD46A;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        CrystalClusterSubmitter.Look plain = state.look;
        if (plain == null) {
            return;
        }
        PrismCrystal.standOnLandingFace(poseStack, state.facing);
        CrystalClusterSubmitter.submit(poseStack, nodeCollector, PrismCrystal.PRISMS, verdantLook(plain),
                state.lightCoords);
    }

    /**
     * The verdant column's look: the plain crystal's sprite, its color
     * multiplied by leaf-green at the crystal's own alpha.
     *
     * @param plain the plain prism's look
     * @return the leaf-green look
     */
    static CrystalClusterSubmitter.Look verdantLook(CrystalClusterSubmitter.Look plain) {
        return new CrystalClusterSubmitter.Look(plain.uv(), ARGB.multiply(plain.color(), LEAF_GREEN));
    }
}
