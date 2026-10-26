package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that clickable runs in the builder draw as a bordered button and plain runs as none, with mocked graphics
 * and no Screen (decision blue-text-options-draw-as-bordered-buttons).
 */
class ControlFlowTest {
    private static final List<ExpressionLines.Run> REF = List.of(new ExpressionLines.Run(ExpressionLines.Kind.LINK,
            "waxed", "waxed"));

    private static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * A pressed run lays the painter's border, then its inner fill, and a target covering the whole box.
     */
    @Test
    void pressedRunsDrawAsAButton() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ClickTargets targets = new ClickTargets();
        boolean[] pressed = {false};
        new ControlFlow(graphics, font(), targets, null, 0, 400, 0).runs(REF, null, at -> pressed[0] = true);
        int width = "waxed".length() * 6 + 2 * ButtonPainter.PADDING;
        verify(graphics).fill(0, 2, width, 2 + ButtonPainter.HEIGHT, ButtonPainter.BORDER);
        verify(graphics).fill(1, 3, width - 1, 1 + ButtonPainter.HEIGHT, ButtonPainter.FILL);
        assertTrue(targets.click(width - 1, 2 + ButtonPainter.HEIGHT - 1));
        assertTrue(pressed[0]);
    }

    /**
     * A run that takes no click lays no box and no target.
     */
    @Test
    void unpressedRunsDrawNoButton() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        ClickTargets targets = new ClickTargets();
        new ControlFlow(graphics, font(), targets, null, 0, 400, 0).runs(REF, null, null);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(ButtonPainter.BORDER));
        assertFalse(targets.click(1, 5));
    }
}
