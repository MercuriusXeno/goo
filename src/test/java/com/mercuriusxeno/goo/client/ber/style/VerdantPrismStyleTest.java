package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.client.CrystalClusterSubmitter;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.PrismCrystal;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The verdant prism draws the plain prism's column in leaf-green at the
 * crystal's own alpha, over the plain crystal's sprite.
 * verdant-prism-greens-blocks-slowly
 * prism-is-one-pointed-quartz-column
 */
class VerdantPrismStyleTest {

    private static final GooRenderUtil.UvRect PRISM_SPRITE = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.375f, 0.625f);

    @Test
    void verdantLookKeepsTheSpriteAndTintsLeafGreenAtTheCrystalAlpha() {
        CrystalClusterSubmitter.Look plain = PrismCrystal.lookOf(PRISM_SPRITE);

        CrystalClusterSubmitter.Look verdant = VerdantPrismStyle.verdantLook(plain);

        assertEquals(plain.uv(), verdant.uv());
        assertEquals(ARGB.color(ARGB.alpha(plain.color()), VerdantPrismStyle.LEAF_GREEN),
                verdant.color());
    }
}
