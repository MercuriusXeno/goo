package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests where a tooltip's text opens against the element it explains on a 400 by 300 screen, with a 3 pixel pad
 * between the tooltip's text and its frame (decision
 * tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room).
 */
class TooltipAnchorTest {
    private static final int SCREEN_WIDTH = 400;
    private static final int SCREEN_HEIGHT = 300;

    /**
     * An element with room below it opens the tooltip one gap under its bottom edge, the tooltip's left edge on the
     * element's.
     */
    @Test
    void elementWithRoomBelowAnchorsTheTooltipUnderItLeftAligned() {
        GroupsPane.Bounds element = new GroupsPane.Bounds(40, 20, 90, 34);
        assertEquals(new TooltipAnchor.Point(40 + TooltipAnchor.PAD, 34 + TooltipAnchor.GAP),
                TooltipAnchor.place(element, 60, 30, SCREEN_WIDTH, SCREEN_HEIGHT));
    }

    /**
     * An element too near the bottom for the tooltip to fit below grows the tooltip upward, its bottom one gap above
     * the element's top edge.
     */
    @Test
    void elementNearTheBottomAnchorsTheTooltipAboveIt() {
        GroupsPane.Bounds element = new GroupsPane.Bounds(40, 270, 90, 284);
        TooltipAnchor.Point point = TooltipAnchor.place(element, 60, 30, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertEquals(new TooltipAnchor.Point(40 + TooltipAnchor.PAD, 270 - TooltipAnchor.GAP - 30), point);
    }

    /**
     * An element too near the right for the tooltip to fit slides the tooltip left until its frame meets the
     * screen's right edge.
     */
    @Test
    void elementNearTheRightSlidesTheTooltipLeftToFit() {
        GroupsPane.Bounds element = new GroupsPane.Bounds(370, 20, 395, 34);
        TooltipAnchor.Point point = TooltipAnchor.place(element, 60, 30, SCREEN_WIDTH, SCREEN_HEIGHT);
        assertEquals(new TooltipAnchor.Point(SCREEN_WIDTH - TooltipAnchor.PAD - 60, 34 + TooltipAnchor.GAP), point);
    }
}
