package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The one primitive every bordered box in the values manager draws through, each box in a named role reading the
 * palette (decision one-frame-primitive-draws-every-bordered-box): a button, the Items chips and the read-value
 * frame in the button's blue on dark, every popup in its dark chrome, and the builder's nest box, term ring and trash
 * strip with the borders they have always had.
 */
final class Frame {
    /** A button's frame: its blue border round its dark fill, as the Items chips and the read-value frame draw too. */
    static final Role BUTTON = new Role(Palette.BORDER, Palette.FILL, 1, false);
    /** A dropdown's, a picker's and an option list's dark chrome. */
    static final Role POPUP = new Role(Palette.POPUP_BORDER, Palette.POPUP_FILL, 1, false);
    /**
     * A parenthetical's 1 px border; its fill is its depth's tone (decision
     * parentheticals-alternate-gray-tones-by-depth).
     */
    static final Role NEST = new Role(Palette.NEST_BORDER, Palette.CLEAR, 1, true);
    /**
     * A term container's 2 px ring, its segments filling themselves between its seams (decision
     * term-draws-a-two-pixel-brighter-border-with-two-tone-seams).
     */
    static final Role TERM = new Role(Palette.TERM_BORDER, Palette.CLEAR, 2, true);
    /**
     * The trash strip's lighter red border round its tone (decision
     * trash-strip-draws-a-can-and-a-lighter-red-border).
     */
    static final Role TRASH = new Role(Palette.TRASH_BORDER, Palette.TRASH_REST, 1, true);
    // A term seam: its light pixel then its dark one.
    private static final int SEAM_PIXEL = 1;

    private Frame() {
    }

    /**
     * How a bordered box looks.
     *
     * @param border    its border's color
     * @param fill      the color inside the border, clear where nothing fills it
     * @param thickness the border's width
     * @param edges     whether the border draws as four edges round the fill, as a see-through border must so its
     *                  color never lays twice, rather than as one box the fill covers
     */
    record Role(int border, int fill, int thickness, boolean edges) {
        /**
         * @param color the box's color
         * @return the role's frame filled solid in one color, border and inside, as a chip the filter holds draws
         */
        Role solid(int color) {
            return new Role(color, color, thickness, edges);
        }

        /**
         * @param tone the color inside the border
         * @return the role's border round a fill of another tone, as a nest's depth or the trash strip's drag state
         */
        Role toned(int tone) {
            return new Role(border, tone, thickness, edges);
        }
    }

    /**
     * Draws a bordered box.
     *
     * @param graphics the frame's graphics
     * @param role     the box's look
     * @param box      its border's outer edges
     */
    static void draw(GuiGraphicsExtractor graphics, Role role, GroupsPane.Bounds box) {
        int inset = role.thickness();
        int innerTop = box.top() + inset;
        int innerBottom = box.bottom() - inset;
        if (role.edges()) {
            graphics.fill(box.left(), box.top(), box.right(), innerTop, role.border());
            graphics.fill(box.left(), innerBottom, box.right(), box.bottom(), role.border());
            graphics.fill(box.left(), innerTop, box.left() + inset, innerBottom, role.border());
            graphics.fill(box.right() - inset, innerTop, box.right(), innerBottom, role.border());
        } else {
            graphics.fill(box.left(), box.top(), box.right(), box.bottom(), role.border());
        }
        if (role.fill() != Palette.CLEAR) {
            graphics.fill(box.left() + inset, innerTop, box.right() - inset, innerBottom, role.fill());
        }
    }

    /**
     * Draws a button-shaped box.
     *
     * @param graphics the frame's graphics
     * @param role     the box's look
     * @param box      the box
     */
    static void draw(GuiGraphicsExtractor graphics, Role role, ButtonPainter.Box box) {
        draw(graphics, role, new GroupsPane.Bounds(box.left(), box.top(), box.right(), box.top() + box.height()));
    }

    /**
     * Draws a box whose border stands a pixel outside the content it frames, as a popup's chrome stands round its
     * rows.
     *
     * @param graphics the frame's graphics
     * @param role     the box's look
     * @param content  the content's edges, the fill's
     */
    static void around(GuiGraphicsExtractor graphics, Role role, GroupsPane.Bounds content) {
        int out = role.thickness();
        draw(graphics, role, new GroupsPane.Bounds(content.left() - out, content.top() - out, content.right() + out,
                content.bottom() + out));
    }

    /**
     * Fills one segment of a term inside its ring, after a two-tone seam where a segment stands before it.
     *
     * @param graphics  the frame's graphics
     * @param segment   the segment's fill, its left edge where its seam starts
     * @param seamWidth the seam's width before the fill, 0 for the first segment
     */
    static void termSegment(GuiGraphicsExtractor graphics, GroupsPane.Bounds segment, int seamWidth) {
        int fillLeft = segment.left();
        if (seamWidth > 0) {
            graphics.fill(fillLeft, segment.top() - TERM.thickness(), fillLeft + SEAM_PIXEL,
                    segment.bottom() + TERM.thickness(), Palette.SEAM_LIGHT);
            graphics.fill(fillLeft + SEAM_PIXEL, segment.top() - TERM.thickness(), fillLeft + seamWidth,
                    segment.bottom() + TERM.thickness(), Palette.SEAM_DARK);
            fillLeft += seamWidth;
        }
        graphics.fill(fillLeft, segment.top(), segment.right(), segment.bottom(), BUTTON.fill());
    }
}
