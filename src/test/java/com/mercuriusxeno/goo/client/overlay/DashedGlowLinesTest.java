package com.mercuriusxeno.goo.client.overlay;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The knife lines draw a faint core whose halo dims outward and whose dashes
 * scroll along the line (decision knives-aims-a-spread-fan-not-a-blob-line).
 */
class DashedGlowLinesTest {

    private static final int WHITE = 0xFFFFFF;
    private static final int CORE_PASS = 0;
    private static final DashedGlowLines.GlowPalette PALETTE = KnifeAimLineRenderer.KNIFE_LINE_GLOW;

    @Test
    void eachHaloPassDrawsDimmerThanTheCore() {
        int coreAlpha = ARGB.alpha(DashedGlowLines.computeGlowPassColor(WHITE, CORE_PASS, PALETTE));
        for (int pass = 1; pass < PALETTE.passes(); pass++) {
            assertTrue(ARGB.alpha(DashedGlowLines.computeGlowPassColor(WHITE, pass, PALETTE)) < coreAlpha);
        }
    }

    @Test
    void theDashesScrollFurtherAtALaterFrame() {
        assertTrue(DashedGlowLines.dashOffsetAt(2.0) > DashedGlowLines.dashOffsetAt(1.0));
    }
}
