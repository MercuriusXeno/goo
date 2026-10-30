package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests how the manager reads and lays a fraction: an improper amount reduced to its whole and a part under 1, and the
 * part drawn at half scale as its numerator over a bar over its denominator, each centered on the bar.
 */
class StackedFractionsTest {

    /**
     * The server's n/d amount reads as a reduced mixed number: 81/4 as 20 and 1 over 4; a whole amount, or text that
     * is no fraction, reads whole alone.
     */
    @Test
    void improperAmountReadsAsItsWholeAndAPartUnderOne() {
        assertEquals(new StackedFractions.Mixed("20", "1", "4"), StackedFractions.of("81/4"));
        assertEquals(new StackedFractions.Mixed("1", "1", "2"), StackedFractions.of("3/2"));
        assertEquals(new StackedFractions.Mixed("", "1", "3"), StackedFractions.of("1/3"));
        assertEquals(new StackedFractions.Mixed("2", "", ""), StackedFractions.of("8/4"));
        assertEquals(new StackedFractions.Mixed("48", "", ""), StackedFractions.of("48"));
        assertEquals(new StackedFractions.Mixed("1.5k", "", ""), StackedFractions.of("1.5k"));
    }

    /**
     * The stack lays the numerator above a bar as wide as the wider number and the denominator below it, each number
     * centered on the bar, in half-scale space.
     */
    @Test
    void stackCentersEachNumberOnItsBar() {
        StackedFractions.Stack stack = StackedFractions.layStack(100, 40, 6, 12);
        assertEquals(100, stack.barX());
        assertEquals(12, stack.barWidth());
        assertEquals(103, stack.numeratorX());
        assertEquals(100, stack.denominatorX());
        assertTrue(stack.numeratorY() + 7 <= stack.barY(), "numerator ink stands above the bar");
        assertTrue(stack.barY() + StackedFractions.BAR_THICKNESS <= stack.denominatorY(), "bar stands above the denominator");
        assertEquals(40 + StackedFractions.STACK_HEIGHT, stack.denominatorY() + 7);
    }

    /**
     * An amount's width is its whole at full size, a gap, and the stack at half scale; a whole amount is its text.
     */
    @Test
    void widthIsTheWholeThenTheHalfScaleStack() {
        Font font = DrawnFrame.font();
        assertEquals(12 + 1 + 3, StackedFractions.width(font, StackedFractions.of("81/4")));
        assertEquals(3, StackedFractions.width(font, StackedFractions.of("1/3")));
        assertEquals(12, StackedFractions.width(font, StackedFractions.of("48")));
    }

    /**
     * Drawn on a line, 81/4 draws 20 at full size and 1 and 4 at half scale with the bar between them, never the
     * improper text.
     */
    @Test
    void amountDrawsItsWholeAndItsStack() {
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        GuiGraphicsExtractor graphics = frame.graphics;
        StackedFractions.draw(graphics, DrawnFrame.font(), "81/4", 50, 20, 0xFFFFFFFF);
        assertEquals(List.of("20", "1", "4"), frame.texts());
        verify(graphics, atLeastOnce()).fill(anyInt(), anyInt(), anyInt(), anyInt(), anyInt());
    }
}
