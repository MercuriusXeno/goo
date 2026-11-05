package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests the one frame primitive every bordered box draws through, each role's border and fill read off mocked graphics
 * (decision one-frame-primitive-draws-every-bordered-box).
 */
class FrameTest {
    /**
     * Each role carries the palette's border and fill for its box: a button's blue on dark, a popup's dark chrome,
     * the builder's nest, term ring and trash strip as they look today.
     */
    @Test
    void eachRoleCarriesItsBorderAndFill() {
        assertEquals(List.of(Palette.BORDER, Palette.FILL), List.of(Frame.BUTTON.border(), Frame.BUTTON.fill()));
        assertEquals(List.of(Palette.POPUP_BORDER, Palette.POPUP_FILL),
                List.of(Frame.POPUP.border(), Frame.POPUP.fill()));
        assertEquals(Palette.NEST_BORDER, Frame.NEST.border());
        assertEquals(List.of(Palette.TERM_BORDER, 2), List.of(Frame.TERM.border(), Frame.TERM.thickness()));
        assertEquals(List.of(Palette.TRASH_BORDER, Palette.TRASH_REST),
                List.of(Frame.TRASH.border(), Frame.TRASH.fill()));
    }

    /**
     * A button frame lays its border over the whole box, then its fill a pixel in.
     */
    @Test
    void buttonLaysItsBorderThenItsFillAPixelIn() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        Frame.draw(graphics, Frame.BUTTON, new GroupsPane.Bounds(10, 20, 50, 34));
        verify(graphics).fill(10, 20, 50, 34, Palette.BORDER);
        verify(graphics).fill(11, 21, 49, 33, Palette.FILL);
    }

    /**
     * A term ring lays four 2 px edges that never overlap and fills nothing inside, its segments filling themselves.
     */
    @Test
    void termRingLaysFourEdgesAndNoFill() {
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        Frame.draw(graphics, Frame.TERM, new GroupsPane.Bounds(0, 0, 40, 20));
        verify(graphics).fill(0, 0, 40, 2, Palette.TERM_BORDER);
        verify(graphics).fill(0, 18, 40, 20, Palette.TERM_BORDER);
        verify(graphics).fill(0, 2, 2, 18, Palette.TERM_BORDER);
        verify(graphics).fill(38, 2, 40, 18, Palette.TERM_BORDER);
        verify(graphics, never()).fill(anyInt(), anyInt(), anyInt(), anyInt(), eq(Palette.FILL));
    }
}
