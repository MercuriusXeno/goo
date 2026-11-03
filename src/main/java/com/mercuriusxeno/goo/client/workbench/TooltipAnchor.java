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
        int y = element.bottom() + GAP;
        if (y + tooltipHeight + PAD > screenHeight) {
            y = element.top() - GAP - tooltipHeight;
        }
        int x = Math.min(element.left() + PAD, screenWidth - PAD - tooltipWidth);
        return new Point(Math.max(x, PAD), y);
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
