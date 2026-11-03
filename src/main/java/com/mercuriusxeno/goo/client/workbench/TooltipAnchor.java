package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import org.joml.Vector2i;

/**
 * Where a tooltip's text opens against the element it explains, so the tooltip moves only when the element moves:
 * one gap under the element with its left edge on the element's, grown upward from the element's top edge when it
 * would overflow the screen's bottom, and slid left when it would overflow the screen's right.
 * Decision tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room.
 */
final class TooltipAnchor {
    // The tooltip's frame stands this far outside its text on every side.
    static final int PAD = TooltipRenderUtil.PADDING_LEFT;
    // The text's distance from the element's edge: the frame's pad and one pixel between frame and element.
    static final int GAP = PAD + 1;

    private TooltipAnchor() {
    }

    /**
     * The top-left of a tooltip's text.
     *
     * @param x its x
     * @param y its y
     */
    record Point(int x, int y) {
    }

    /**
     * @param element       the bounds of the element the tooltip explains
     * @param tooltipWidth  the width of the tooltip's text
     * @param tooltipHeight the height of the tooltip's text
     * @param screenWidth   the screen's width
     * @param screenHeight  the screen's height
     * @return the top-left of the tooltip's text
     */
    static Point place(GroupsPane.Bounds element, int tooltipWidth, int tooltipHeight, int screenWidth,
                       int screenHeight) {
        int x = Math.min(element.left() + PAD, screenWidth - PAD - tooltipWidth);
        return new Point(Math.max(x, PAD), topOf(element, tooltipHeight, screenHeight));
    }

    // Below where it fits, above where only that fits, and otherwise on the side with more room, kept on the screen.
    private static int topOf(GroupsPane.Bounds element, int tooltipHeight, int screenHeight) {
        int below = element.bottom() + GAP;
        int roomBelow = screenHeight - PAD - below;
        int above = element.top() - GAP - tooltipHeight;
        int roomAbove = element.top() - GAP - PAD;
        if (tooltipHeight <= roomBelow) {
            return below;
        }
        if (tooltipHeight <= roomAbove) {
            return above;
        }
        int onScreen = roomBelow >= roomAbove ? Math.min(below, screenHeight - PAD - tooltipHeight) : PAD;
        return Math.max(onScreen, PAD);
    }

    /**
     * @param element      the bounds of the element the tooltip explains
     * @param screenHeight the screen's height
     * @return the tallest tooltip text that fits on one side of the element
     */
    static int roomFor(GroupsPane.Bounds element, int screenHeight) {
        return Math.max(screenHeight - PAD - element.bottom() - GAP, element.top() - GAP - PAD);
    }

    /**
     * @param element the bounds of the element the tooltip explains
     * @return a positioner opening the tooltip against the element, wherever the cursor stands
     */
    static ClientTooltipPositioner anchoredTo(GroupsPane.Bounds element) {
        return (screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight) -> {
            Point point = place(element, tooltipWidth, tooltipHeight, screenWidth, screenHeight);
            return new Vector2i(point.x(), point.y());
        };
    }
}
