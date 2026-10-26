package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a goo amount tooltip line measures its 16px type icon, a 2px gap and its amount, with a mocked font
 * (decision value-tooltips-draw-each-types-icon-before-its-amount).
 */
class GooAmountTooltipComponentTest {

    @Test
    void lineMeasuresTheIconItsGapAndTheAmount() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        GooAmountTooltipComponent line = new GooAmountTooltipComponent(new TooltipLine.GooAmount("metal", "3/2"));
        assertTrue(line.getHeight(font) >= GooTypeIcons.SIZE, "height " + line.getHeight(font));
        assertEquals(GooTypeIcons.SIZE + 2 + 18, line.getWidth(font));
    }
}
