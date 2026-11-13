package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;

/**
 * A drag rail reads as a thing to drag: a bar with three grip lines in it (decision
 * rails-fill-the-left-edge-of-their-box).
 */
class RailsTest {
    private static final int LEFT = 10;
    private static final int TOP = 20;
    private static final int BOTTOM = 36;

    // Each fill the graphics took in the color, as left, top, right and bottom.
    static List<List<Integer>> fillsIn(GuiGraphicsExtractor graphics, int color) {
        return mockingDetails(graphics).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("fill") && call.getArguments().length == 5
                        && (int) call.getArgument(4) == color)
                .map(call -> List.<Integer>of(call.getArgument(0), call.getArgument(1), call.getArgument(2),
                        call.getArgument(3)))
                .toList();
    }

    @Test
    void aRailFillsItsBarAndCarriesThreeOnePixelGripLinesInsideIt() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);

        Rails.draw(graphics, LEFT, TOP, BOTTOM);

        verify(graphics).fill(LEFT, TOP, LEFT + ControlFlow.RAIL_WIDTH, BOTTOM, Palette.RAIL);
        List<List<Integer>> grips = fillsIn(graphics, Palette.GRIP);
        assertEquals(3, grips.size(), grips.toString());
        grips.forEach(grip -> assertEquals(List.of(true, 1), List.of(grip.get(0) > LEFT
                && grip.get(2) < LEFT + ControlFlow.RAIL_WIDTH && grip.get(1) > TOP && grip.get(3) < BOTTOM,
                grip.get(3) - grip.get(1)), grip + " inside the bar, one pixel tall"));
    }
}
