package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    /**
     * An exclusive set draws one button reading the chosen option; its click lists every option under it, and a pick
     * runs that option and closes the list (decision exclusive-choice-sets-draw-as-dropdowns).
     */
    @Test
    void choicesDrawOneButtonWhoseClickListsTheSet() {
        BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        List<String> picked = new java.util.ArrayList<>();
        List<BuilderOverlays.Option> options = List.of(
                new BuilderOverlays.Option("north", "go north", at -> picked.add("north")),
                new BuilderOverlays.Option("south", "go south", at -> picked.add("south")));
        DrawnFrame closed = new DrawnFrame();
        new ControlFlow(closed.graphics, font(), closed.targets, null, 0, 400, 0).choices(options, 1, overlays);
        assertEquals(List.of(ControlFlow.dropdownLabel("south")), closed.texts());
        assertEquals(List.of(BuilderOverlays.hintKey("go south")), closed.targets.hoverKeys());
        closed.click(ControlFlow.dropdownLabel("south"));
        DrawnFrame open = new DrawnFrame();
        overlays.draw(open.graphics, font(), open.targets);
        assertEquals(List.of("north", "south"), open.texts());
        assertTrue(open.targets.hoverKeys().containsAll(List.of(BuilderOverlays.hintKey("go north"),
                BuilderOverlays.hintKey("go south"))));
        open.click("north");
        assertEquals(List.of("north"), picked);
        assertFalse(overlays.listOpen());
    }
}
