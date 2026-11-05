package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that a goo amount tooltip line measures its type icon at the texture's own size, its gap and its amount, and
 * draws the amount through SmallDigits, with a mocked font (decisions
 * value-tooltips-draw-each-types-icon-before-its-amount, goo-type-icons-draw-native-everywhere,
 * goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
 */
class GooAmountTooltipComponentTest {

    @Test
    void lineMeasuresTheIconAtItsTextureSizeItsGapAndTheAmount() {
        Font font = DrawnFrame.font();
        for (int size : new int[] {11, 12}) {
            GooAmountTooltipComponent line = new GooAmountTooltipComponent(new TooltipLine.GooAmount("metal", "3/2"),
                    type -> size);
            assertEquals(size + 2, line.getHeight(font));
            // The gap a pixel short, then the small amount and the pixel it leaves before the next line's edge.
            assertEquals(size + 2 - 1 + SmallDigits.advance(font, "3/2"), line.getWidth(font));
        }
    }

    @Test
    void amountDrawsThroughSmallDigits() {
        DrawnFrame frame = new DrawnFrame();
        new GooAmountTooltipComponent(new TooltipLine.GooAmount("metal", "3"), type -> 11)
                .extractImage(DrawnFrame.font(), 0, 0, 100, 20, frame.graphics);
        assertEquals(Lettering.AMOUNT_SCALE, frame.placed("3").scale());
    }
}
