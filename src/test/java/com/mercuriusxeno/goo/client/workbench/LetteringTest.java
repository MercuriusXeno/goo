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
     * top to bottom, so + and - stand in the middle of their buttons (the operator saw them too far left and slightly
     * too high).
     */
    @Test
    void labelInkCentersOnItsButton() {
        Font font = DrawnFrame.font();
        Lettering letters = new Lettering(font, ControlFlow.BUILDER_SCALE);
        ButtonPainter.Box button = new ButtonPainter.Box(10, 20, ButtonPainter.width(letters, "+"),
                ButtonPainter.HEIGHT);
        float[] origin = letters.labelOrigin(button, "+");
        float inkWidth = (font.width("+") - 1) * ControlFlow.BUILDER_SCALE;
        float inkHeight = (font.lineHeight - 2) * ControlFlow.BUILDER_SCALE;
        assertEquals(button.left() + button.width() / 2f, origin[0] + inkWidth / 2, NEAR, "left to right");
        assertEquals(button.top() + button.height() / 2f, origin[1] + inkHeight / 2, NEAR, "top to bottom");
    }
}
