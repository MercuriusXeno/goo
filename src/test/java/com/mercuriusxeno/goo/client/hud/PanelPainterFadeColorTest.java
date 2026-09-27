package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that PanelPainter.fadeColor scales a packed ARGB color's alpha by the
 * panel's fade and keeps its RGB (decision diagnose-then-fix-hud-panel-fade).
 */
class PanelPainterFadeColorTest {

    /** Gold label color, opaque. */
    private static final int GOLD = 0xFFFFAA00;

    @Test
    void halfOpacityHalvesAlphaAndKeepsRgb() {
        int faded = PanelPainter.fadeColor(GOLD, 0.5f);
        assertEquals(0xFFAA00, faded & 0xFFFFFF);
        assertEquals(0x7F, faded >>> 24);
    }

    @Test
    void zeroOpacityAnswersZeroAlpha() {
        assertEquals(0, PanelPainter.fadeColor(GOLD, 0f) >>> 24);
    }

    @Test
    void fullOpacityAnswersTheOriginal() {
        assertEquals(GOLD, PanelPainter.fadeColor(GOLD, 1f));
    }

    @Test
    void aTranslucentColorScalesFromItsOwnAlpha() {
        assertEquals(0x3F, PanelPainter.fadeColor(0x7F123456, 0.5f) >>> 24);
    }
}
