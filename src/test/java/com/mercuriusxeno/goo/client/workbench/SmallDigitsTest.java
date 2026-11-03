package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests where a goo amount beside its type icon lands and at what size, on mocked graphics with a real pose stack
 * (decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
 */
class SmallDigitsTest {
    private static final int ICON_RIGHT = 20;
    private static final int ICON_TOP = 10;
    private static final int ICON_SIZE = 11;
    private static final int GAP = 2;

    @Test
    void amountDrawsSmallerAPixelLowerAndAPixelCloserToItsIcon() {
        DrawnFrame frame = new DrawnFrame();
        SmallDigits.drawBesideIcon(frame.graphics, DrawnFrame.font(), "3",
                new SmallDigits.Icon(ICON_RIGHT, ICON_TOP, ICON_SIZE, GAP), 0xFFFFFFFF);
        DrawnFrame.Placed amount = frame.placed("3");
        assertTrue(amount.scale() < 1, "the digits draw below full size at " + amount.scale());
        assertEquals(ICON_TOP + GooTypeIcons.centered(ICON_SIZE, SmallDigits.GLYPH_HEIGHT) + 1, amount.y(),
                "a pixel under the icon's centre");
        assertEquals(ICON_RIGHT + GAP - 1, amount.x(), "a pixel nearer the icon than the gap");
    }

    @Test
    void advanceLeavesAPixelMoreBeforeTheNextIcon() {
        Font font = DrawnFrame.font();
        assertEquals(SmallDigits.width(font, "12") + 1, SmallDigits.advance(font, "12"));
        assertTrue(SmallDigits.width(font, "12") < font.width("12"), "the digits take less room than full size");
    }
}
