package com.mercuriusxeno.goo.client.hud;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests that PanelPainter.rowGeometry centers a row's glyph block on its icon
 * (decision diagnose-then-fix-goo-count-alignment). The glyph height is 7: the
 * vanilla font's ascii.png provider in assets/minecraft/font/include/default.json
 * (the client-extra jar) declares ascent 7 on 8-pixel cells, and reading
 * textures/font/ascii.png shows digits 0 to 9 inked in pixel rows 0 to 6 of their
 * cells, drawn from the text top with no shadow (InWorldHud.drawText passes
 * dropShadow false). The font's lineHeight is 9, so centering that line box
 * as drawRow once did leaves the digits one pixel above the icon's center.
 */
class PanelPainterRowGeometryTest {

    /** The vanilla font's line box height, which drawRow once centered by. */
    private static final float VANILLA_LINE_HEIGHT = 9f;
    /** A row top away from zero, so an offset error cannot hide behind it. */
    private static final float ROW_TOP = 40f;
    /** Half a pixel, the tolerance the criterion grants. */
    private static final float HALF_PIXEL = 0.5f;

    @Test
    void lineBoxCenteringSetTheDigitsAboveTheIcon() {
        float iconCenter = ROW_TOP + (PanelPainter.ROW_HEIGHT - PanelPainter.ICON_SIZE) / 2 + PanelPainter.ICON_SIZE / 2;
        float oldTextTop = ROW_TOP + (PanelPainter.ROW_HEIGHT - VANILLA_LINE_HEIGHT) / 2;
        float oldGlyphCenter = oldTextTop + PanelPainter.DIGIT_GLYPH_HEIGHT / 2;
        assertEquals(1f, iconCenter - oldGlyphCenter);
    }

    @Test
    void iconRowCentersTheGlyphBlockOnTheIcon() {
        PanelPainter.RowGeometry geometry = PanelPainter.rowGeometry(ROW_TOP, true, PanelPainter.DIGIT_GLYPH_HEIGHT);
        float iconCenter = geometry.iconTop() + PanelPainter.ICON_SIZE / 2;
        float glyphCenter = geometry.textTop() + PanelPainter.DIGIT_GLYPH_HEIGHT / 2;
        assertEquals(iconCenter, glyphCenter, HALF_PIXEL);
        assertEquals(ROW_TOP + 0.5f, geometry.iconTop());
        assertEquals(ROW_TOP + 2f, geometry.textTop());
    }

    @Test
    void headerRowCentersTheGlyphBlockOnTheRow() {
        PanelPainter.RowGeometry geometry = PanelPainter.rowGeometry(ROW_TOP, false, PanelPainter.DIGIT_GLYPH_HEIGHT);
        float rowCenter = ROW_TOP + PanelPainter.ROW_HEIGHT / 2;
        assertEquals(rowCenter, geometry.textTop() + PanelPainter.DIGIT_GLYPH_HEIGHT / 2, HALF_PIXEL);
    }

    @Test
    void textTopFollowsTheGlyphHeightNotALineBox() {
        PanelPainter.RowGeometry seven = PanelPainter.rowGeometry(ROW_TOP, true, 7f);
        PanelPainter.RowGeometry nine = PanelPainter.rowGeometry(ROW_TOP, true, 9f);
        assertNotEquals(seven.textTop(), nine.textTop());
        assertEquals(1f, seven.textTop() - nine.textTop());
    }
}
