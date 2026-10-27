package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * One frame a pane drew onto mocked graphics with a mocked font six pixels a character wide: the texts it drew, where
 * each stood and in which color, and its click areas, so a test clicks a control by its label.
 */
final class DrawnFrame {
    final GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
    final ClickTargets targets = new ClickTargets();

    DrawnFrame() {
        when(graphics.guiWidth()).thenReturn(SCREEN_WIDTH);
        when(graphics.guiHeight()).thenReturn(SCREEN_HEIGHT);
    }

    /** The mocked screen's width. */
    static final int SCREEN_WIDTH = 600;
    /** The mocked screen's height. */
    static final int SCREEN_HEIGHT = 400;

    /**
     * One text drawn.
     *
     * @param text  the string
     * @param x     its left edge
     * @param y     its top edge
     * @param color its color
     */
    record Text(String text, int x, int y, int color) {
    }

    /**
     * @return a font six pixels a character wide that cuts nothing
     */
    static Font font() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * @return every text drawn, in order
     */
    List<Text> drawn() {
        ArgumentCaptor<String> texts = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> colors = ArgumentCaptor.forClass(Integer.class);
        verify(graphics, atLeast(0)).text(any(Font.class), texts.capture(), xs.capture(), ys.capture(),
                colors.capture());
        List<Text> drawn = new ArrayList<>();
        for (int index = 0; index < texts.getAllValues().size(); index++) {
            drawn.add(new Text(texts.getAllValues().get(index), xs.getAllValues().get(index),
                    ys.getAllValues().get(index), colors.getAllValues().get(index)));
        }
        return drawn;
    }

    /**
     * @return every string drawn, in order
     */
    List<String> texts() {
        return drawn().stream().map(Text::text).toList();
    }

    /**
     * @param label a drawn string
     * @return the last place it was drawn
     */
    Text find(String label) {
        List<Text> matches = drawn().stream().filter(text -> text.text().equals(label)).toList();
        assertTrue(!matches.isEmpty(), label + " not drawn in " + texts());
        return matches.getLast();
    }

    /**
     * Clicks inside the text last drawn as the label.
     *
     * @param label a drawn string
     */
    void click(String label) {
        Text text = find(label);
        assertTrue(targets.click(text.x() + 1, text.y() + 1), label + " took no click");
    }
}
