package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the scroll bar's thumb, clamp, wheel and drag with no Screen (decision every-tab-draws-a-scroll-bar).
 */
class ScrollBarTest {
    private static final int TRACK_TOP = 20;
    private static final int TRACK_HEIGHT = 100;
    private static final int VIEW = 100;
    private static final int STEP = 10;

    /**
     * Content shorter than the view draws a full thumb and holds no offset, whatever the wheel does.
     */
    @Test
    void shortContentDrawsAFullThumbAndHoldsNoOffset() {
        ScrollBar bar = new ScrollBar(STEP);
        bar.measure(VIEW / 2, VIEW);
        bar.wheel(-1);
        assertEquals(0, bar.offset());
        assertEquals(new ScrollBar.Thumb(TRACK_TOP, TRACK_HEIGHT), bar.thumb(TRACK_TOP, TRACK_HEIGHT));
    }

    /**
     * Content twice the view draws a half thumb, which stands at half the track once the offset reaches the end,
     * and an offset past the end clamps to it.
     */
    @Test
    void contentTwiceTheViewDrawsAHalfThumbAtTheEnd() {
        ScrollBar bar = new ScrollBar(STEP);
        bar.measure(VIEW * 2, VIEW);
        bar.scrollTo(VIEW * 10);
        assertEquals(VIEW, bar.offset());
        assertEquals(new ScrollBar.Thumb(TRACK_TOP + TRACK_HEIGHT / 2, TRACK_HEIGHT / 2),
                bar.thumb(TRACK_TOP, TRACK_HEIGHT));
    }

    /**
     * A wheel turn toward the player steps the offset down the content and a turn away steps it back, never above
     * the top.
     */
    @Test
    void wheelStepsTheOffset() {
        ScrollBar bar = new ScrollBar(STEP);
        bar.measure(VIEW * 2, VIEW);
        bar.wheel(-1);
        assertEquals(STEP, bar.offset());
        bar.wheel(1);
        bar.wheel(1);
        assertEquals(0, bar.offset());
    }

    /**
     * Dragging the thumb to the track's bottom scrolls to the content's end, and to its top back to the start.
     */
    @Test
    void dragToTheTracksBottomReachesTheEnd() {
        ScrollBar bar = new ScrollBar(STEP);
        bar.measure(VIEW * 2, VIEW);
        bar.drag(TRACK_TOP + TRACK_HEIGHT, TRACK_TOP, TRACK_HEIGHT);
        assertEquals(VIEW, bar.offset());
        bar.drag(TRACK_TOP, TRACK_TOP, TRACK_HEIGHT);
        assertEquals(0, bar.offset());
    }

    /**
     * A tailing bar standing at the end follows the content as it grows, and one the wheel moved off the end stays.
     */
    @Test
    void tailingBarFollowsGrowingContentFromTheEnd() {
        ScrollBar bar = ScrollBar.tailing(1);
        bar.measure(12, 8);
        assertEquals(4, bar.offset());
        bar.measure(14, 8);
        assertEquals(6, bar.offset());
        bar.wheel(1);
        bar.measure(16, 8);
        assertEquals(5, bar.offset());
    }
}
