package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.ber.PrismRenderState;
import com.mercuriusxeno.goo.client.ber.TintedBlockCollector;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * A verdant prism's look: the plain crystal drawn tinted leaf-green, while
 * the breeze it puffs on each pulse and the sparkles where it greens come
 * from the server.
 * verdant-prism-greens-blocks-slowly
 */
public final class VerdantPrismStyle implements PrismComboStyle {

    /** The id of the ability whose program is the verdant combo. */
    public static final String COMBO = "goo:leaf_verdant";
    /** A leaf-green the milky crystal is multiplied by. */
    private static final int LEAF_GREEN = 0xFF8FD46A;
    private static final int NO_OUTLINE = 0;

    @Override
    public void submit(PrismRenderState state, PoseStack poseStack, SubmitNodeCollector nodeCollector) {
        state.crystal.submit(poseStack, new TintedBlockCollector(nodeCollector, LEAF_GREEN), state.lightCoords,
                OverlayTexture.NO_OVERLAY, NO_OUTLINE);
    }
}
