package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests where a lettered label stands in its button, on a mocked font six pixels a character wide, each glyph's ink
 * a pixel short of its advance, on a nine-pixel line whose glyphs' ink is seven rows tall.
 */
class LetteringTest {
    private static final float NEAR = 0.01f;

    /**
     * An operator's label lettered at the builder's scale centers its glyph's ink on its button, left to right and
     * top to bottom, within half a screen pixel, so + and - stand in the middle of their buttons (the operator saw them
     * too far left and slightly too high).
     */
    @Test
    void labelInkCentersOnItsButton() {
        Font font = DrawnFrame.font();
        Lettering letters = new Lettering(font, ControlFlow.BUILDER_SCALE);
        ButtonPainter.Box button = new ButtonPainter.Box(10, 20, ButtonPainter.width(letters, "+"),
                ButtonPainter.HEIGHT);
        float[] origin = letters.labelOrigin(button, "+", 4);
        float inkWidth = (font.width("+") - 1) * ControlFlow.BUILDER_SCALE;
        float inkHeight = (font.lineHeight - 2) * ControlFlow.BUILDER_SCALE;
        float halfPixel = 0.5f / 4 + NEAR;
        assertEquals(button.left() + button.width() / 2f, origin[0] + inkWidth / 2, halfPixel, "left to right");
        assertEquals(button.top() + button.height() / 2f, origin[1] + inkHeight / 2, halfPixel, "top to bottom");
    }

    /**
     * A scaled label's origin lands on a whole screen pixel at every GUI scale, so its glyph's texels fall on the
     * same screen pixels in every button and no operator draws one column or row stretched (the operator saw the +
     * of one row stretched where another row's drew clean); it stands within half a screen pixel of centered.
     */
    @Test
    void labelOriginLandsOnAWholeScreenPixel() {
        Font font = DrawnFrame.font();
        Lettering letters = new Lettering(font, ControlFlow.BUILDER_SCALE);
        float inkWidth = (font.width("+") - 1) * ControlFlow.BUILDER_SCALE;
        float inkHeight = (font.lineHeight - 2) * ControlFlow.BUILDER_SCALE;
        for (int guiScale = 1; guiScale <= 6; guiScale++) {
            for (int left = 10; left < 14; left++) {
                ButtonPainter.Box button = new ButtonPainter.Box(left, 21, ButtonPainter.width(letters, "+") + left % 2,
                        ButtonPainter.HEIGHT);
                float[] origin = letters.labelOrigin(button, "+", guiScale);
                String at = "gui scale " + guiScale + ", button at " + left + ": " + origin[0] + ", " + origin[1];
                assertEquals(Math.round(origin[0] * guiScale), origin[0] * guiScale, NEAR, at);
                assertEquals(Math.round(origin[1] * guiScale), origin[1] * guiScale, NEAR, at);
                float halfPixel = 0.5f / guiScale + NEAR;
                assertEquals(button.left() + button.width() / 2f, origin[0] + inkWidth / 2, halfPixel, at);
                assertEquals(button.top() + button.height() / 2f, origin[1] + inkHeight / 2, halfPixel, at);
            }
        }
    }
}
