package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests that a goo amount tooltip line measures its type icon at the texture's own size, a 2px gap and its amount,
 * with a mocked font (decisions value-tooltips-draw-each-types-icon-before-its-amount,
 * goo-type-icons-draw-native-everywhere).
 */
class GooAmountTooltipComponentTest {

    @Test
    void lineMeasuresTheIconAtItsTextureSizeItsGapAndTheAmount() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        for (int size : new int[] {11, 12}) {
            GooAmountTooltipComponent line = new GooAmountTooltipComponent(new TooltipLine.GooAmount("metal", "3/2"),
                    type -> size);
            assertEquals(size + 2, line.getHeight(font));
            // 3/2 reads 1 and 1 over 2: the whole's 6px, a 1px gap and the half-scale stack's 3px.
            assertEquals(size + 2 + 6 + 1 + 3, line.getWidth(font));
        }
    }
}
