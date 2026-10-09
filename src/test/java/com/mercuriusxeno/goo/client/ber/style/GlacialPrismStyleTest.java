package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A glacial prism's column takes a deep glacier blue over the quartz at the
 * crystal's own translucency (decision glacial-prism-holds-the-area-frozen).
 */
class GlacialPrismStyleTest {

    /** The plain column's color: white at the crystal's translucency. */
    private static final int PLAIN = ARGB.color(170, 0xFFFFFF);

    @Test
    void theColumnKeepsTheCrystalsTranslucency() {
        assertEquals(ARGB.alpha(PLAIN), ARGB.alpha(GlacialPrismStyle.iced(PLAIN)));
    }

    @Test
    void theColumnLeansDeepBlue() {
        int iced = GlacialPrismStyle.iced(PLAIN);
        assertTrue(ARGB.blue(iced) > ARGB.green(iced) && ARGB.green(iced) > ARGB.red(iced),
                "blue leads green, green leads red");
        assertTrue(ARGB.red(iced) < 128, "deep, not pale");
    }
}
