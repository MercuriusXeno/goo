package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.workbench.Lettering;

/**
 * Where the known items menu's header lays each part, from the frame's width and the target's measured widths, so a
 * test grades the geometry with no client. On the left a column reserved for the plexer's current target whether or
 * not one stands: the Target label beside the target's icon, the target's name beneath in a smaller font and a second
 * line reserved for a name that spills the column. A vertical rule, then on the right the title on the close button's
 * row with the search bar beneath it. A horizontal rule runs under both columns and the grid starts under it.
 * decision menu-header-reserves-the-target
 *
 * @param target          the target column's parts
 * @param verticalRuleX   the one-pixel vertical rule's x, between the columns
 * @param title           the title's box, on the close button's row
 * @param search          the search bar's box, inside its one-pixel border
 * @param horizontalRuleY the one-pixel horizontal rule's y, under both columns
 * @param gridTop         the grid's top edge, under the horizontal rule
 */
record HeaderLayout(TargetColumn target, int verticalRuleX, Area title, Area search, int horizontalRuleY,
                    int gridTop) {

    /** The room the target's name takes across, in pixels at the smaller scale. */
    static final int COLUMN_WIDTH = 100;
    /** The vanilla font's line height, which every line of the header lays by. */
    static final int FONT_LINE_HEIGHT = 9;
    /** A name line's height at the smaller scale, the workbench's amount scale, rounded up. */
    static final int NAME_LINE_HEIGHT = (int) Math.ceil(FONT_LINE_HEIGHT * Lettering.AMOUNT_SCALE);
    private static final int MARGIN = KnownItemsOverlayRenderer.MARGIN;
    private static final int GAP = KnownItemsOverlayRenderer.GAP;
    private static final int ICON = KnownItemsOverlayRenderer.ICON;
    private static final int ROW_HEIGHT = KnownItemsOverlayRenderer.SEARCH_HEIGHT;
    /** The room between the label row and the name. */
    private static final int NAME_GAP = 2;
    private static final int SIDES = 2;
    /** The lines a name takes once it spills the column's first line. */
    private static final int SPILLED = 2;
    /** A one-pixel rule or border. */
    private static final int RULE = 1;

    /**
     * Lays the header.
     *
     * @param frameWidth the frame's width
     * @param labelWidth the Target label's width at full size
     * @param nameWidth  the target's name's width at the smaller scale, 0 when no target stands
     * @return the header's parts
     */
    static HeaderLayout of(int frameWidth, int labelWidth, int nameWidth) {
        TargetColumn target = TargetColumn.of(labelWidth, nameWidth);
        int ruleX = target.column().right() + GAP;
        int rightLeft = ruleX + RULE + GAP;
        KnownItemsOverlayRenderer.Box close = KnownItemsOverlayRenderer.closeButton(frameWidth);
        Area title = new Area(rightLeft, close.top(), close.left() - GAP - rightLeft, ROW_HEIGHT);
        int searchLeft = rightLeft + RULE;
        Area search = new Area(searchLeft, title.bottom() + GAP + RULE, frameWidth - MARGIN - RULE - searchLeft,
                ROW_HEIGHT);
        int horizontalRuleY = Math.max(target.column().bottom(), search.bottom() + RULE) + GAP;
        return new HeaderLayout(target, ruleX, title, search, horizontalRuleY, horizontalRuleY + RULE + GAP);
    }

    /**
     * A box on the frame.
     *
     * @param left   its left edge
     * @param top    its top edge
     * @param width  its width
     * @param height its height
     */
    record Area(int left, int top, int width, int height) {
        /**
         * @return its right edge, one past its last column
         */
        int right() {
            return left + width;
        }

        /**
         * @return its bottom edge, one past its last row
         */
        int bottom() {
            return top + height;
        }
    }

    /**
     * The target column's parts, the same boxes whether or not a target stands.
     *
     * @param column     the whole column
     * @param label      the Target label's box
     * @param icon       the target's icon square, after the label
     * @param firstLine  the name's first line, beneath the label row
     * @param secondLine the line reserved for a name that spills the first
     * @param nameLines  the lines the name takes, 1, or 2 when it spills the column
     */
    record TargetColumn(Area column, Area label, Area icon, Area firstLine, Area secondLine, int nameLines) {
        /**
         * @param labelWidth the Target label's width at full size
         * @param nameWidth  the target's name's width at the smaller scale
         * @return the column laid from the top left margin
         */
        static TargetColumn of(int labelWidth, int nameWidth) {
            Area label = new Area(MARGIN, MARGIN + (ICON - FONT_LINE_HEIGHT + RULE) / SIDES, labelWidth,
                    FONT_LINE_HEIGHT);
            Area icon = new Area(label.right() + GAP, MARGIN, ICON, ICON);
            Area first = new Area(MARGIN, MARGIN + ICON + NAME_GAP, COLUMN_WIDTH, NAME_LINE_HEIGHT);
            Area second = new Area(MARGIN, first.bottom() + RULE, COLUMN_WIDTH, NAME_LINE_HEIGHT);
            Area column = new Area(MARGIN, MARGIN, COLUMN_WIDTH, second.bottom() - MARGIN);
            return new TargetColumn(column, label, icon, first, second, nameWidth > COLUMN_WIDTH ? SPILLED : 1);
        }
    }
}
