package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the box a button takes and the click target it reserves (decision blue-text-options-draw-as-bordered-buttons).
 */
class ButtonPainterTest {
    private static final int LABEL_WIDTH = 30;
    private static final int X = 10;
    private static final int LINE_TOP = 40;
    private static final int LINE_HEIGHT = 18;

    /**
     * A label's box spans its width plus padding on both sides, 14px high, centered on its line.
     */
    @Test
    void boxSpansTheLabelAndItsPadding() {
        ButtonPainter.Box box = ButtonPainter.layout(LABEL_WIDTH, X, LINE_TOP, LINE_HEIGHT);
        assertEquals(new ButtonPainter.Box(X, LINE_TOP + 2, LABEL_WIDTH + 2 * ButtonPainter.PADDING, 14), box);
    }

    /**
     * The reserved target answers a click on every corner of the box and none a pixel past it.
     */
    @Test
    void targetCoversTheBoxAndNothingPastIt() {
        ButtonPainter.Box box = ButtonPainter.layout(LABEL_WIDTH, X, LINE_TOP, LINE_HEIGHT);
        ClickTargets targets = new ClickTargets();
        AtomicInteger clicks = new AtomicInteger();
        ButtonPainter.reserve(targets, box, clicks::incrementAndGet, null);
        int right = box.right() - 1;
        int bottom = box.top() + box.height() - 1;
        assertTrue(targets.click(box.left(), box.top()));
        assertTrue(targets.click(right, bottom));
        assertFalse(targets.click(box.left() - 1, box.top()));
        assertFalse(targets.click(box.right(), bottom));
        assertFalse(targets.click(right, box.top() - 1));
        assertFalse(targets.click(right, bottom + 1));
        assertEquals(2, clicks.get());
    }
}
