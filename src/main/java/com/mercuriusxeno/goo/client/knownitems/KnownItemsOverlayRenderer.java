package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.network.GhostItemPickPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the known items overlay: one darkening fill over the whole frame and one item icon per known
 * item in a centered grid, nothing else, and names the item the cursor rests on.
 * decision menu-is-a-darkening-overlay-not-a-screen
 * decision g-opens-the-known-items-menu-empty-handed
 */
public final class KnownItemsOverlayRenderer {

    /** The darkening fill's color: the world stays dimly in view behind the items. */
    static final int DARKEN = 0xC8000000;
    /** The side of the square each item takes in the grid, its icon and the gap around it. */
    static final int CELL = 20;
    /** The side of an item icon. */
    static final int ICON = 16;
    /** The space kept clear between the grid and the frame's edges. */
    static final int MARGIN = 24;
    /** A span's two sides, which a centered span's slack splits between. */
    private static final int SIDES = 2;
    /** The scroll bar's width. */
    static final int BAR_WIDTH = 6;
    /** The search bar's box color, a flat fill a shade off the darkening. */
    static final int SEARCH_BAR = 0xFF202020;
    /** The search text's color. */
    static final int SEARCH_TEXT = 0xFFE0E0E0;
    /** The caret drawn after the typed text. */
    static final String CARET = "_";
    /** The search bar's height. */
    static final int SEARCH_HEIGHT = 12;
    /** The search text's inset from the bar's top-left corner. */
    static final int SEARCH_INSET = 2;
    /** The gap between the search bar and the grid, and between the grid and the scroll bar. */
    static final int GAP = 4;
    /** The scroll bar's track color. */
    static final int TRACK = 0xFF202020;
    /** The scroll bar's thumb color. */
    static final int THUMB = 0xFF808080;

    private KnownItemsOverlayRenderer() {}

    /**
     * One item the player knows.
     *
     * @param id    the item's registry id
     * @param stack a stack of the item, drawn as its icon
     */
    public record KnownItem(Identifier id, ItemStack stack) {
    }

    /**
     * Where one known item's icon drew.
     *
     * @param item the known item
     * @param left the icon's left edge
     * @param top  the icon's top edge
     */
    public record ItemIcon(KnownItem item, int left, int top) {

        /**
         * Whether a point lies on the icon.
         *
         * @param x the point's x
         * @param y the point's y
         * @return true inside the icon's square
         */
        public boolean contains(double x, double y) {
            return x >= left && x < left + ICON && y >= top && y < top + ICON;
        }
    }

    /**
     * What one overlay frame draws.
     *
     * @param width  the frame's width
     * @param height the frame's height
     * @param items    the known items in the order they draw, already filtered by the query
     * @param query    the text typed in the search bar
     * @param firstRow the grid row the scroll asks to show first
     */
    public record Frame(int width, int height, List<KnownItem> items, String query, int firstRow) {
    }

    /**
     * What a frame drew that the overlay's input reads back.
     *
     * @param icons  every icon drawn, where it drew
     * @param scroll the grid's scroll, its first row clamped
     * @param track  where the scroll bar's track drew, null when every row shows
     */
    public record Drawn(List<ItemIcon> icons, ScrollModel scroll, @Nullable Track track) {
    }

    /**
     * Where the scroll bar's track drew.
     *
     * @param left   the track's left edge
     * @param top    the track's top edge
     * @param length the track's length down the grid
     */
    public record Track(int left, int top, int length) {
    }

    /**
     * Draws the frame.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font the search text draws in
     * @param frame    what the frame draws
     * @return what the frame drew
     */
    public static Drawn render(GuiGraphicsExtractor graphics, Font font, Frame frame) {
        graphics.fill(0, 0, frame.width(), frame.height(), DARKEN);
        Grid grid = Grid.of(frame);
        graphics.fill(grid.left(), MARGIN, grid.right(), MARGIN + SEARCH_HEIGHT, SEARCH_BAR);
        graphics.text(font, frame.query() + CARET, grid.left() + SEARCH_INSET, MARGIN + SEARCH_INSET, SEARCH_TEXT);
        int count = frame.items().size();
        ScrollModel scroll = ScrollModel.of((count + grid.columns() - 1) / grid.columns(), grid.rowsVisible(),
                frame.firstRow());
        int inset = (CELL - ICON) / SIDES;
        int first = scroll.firstRow() * grid.columns();
        int end = Math.min(count, first + grid.rowsVisible() * grid.columns());
        List<ItemIcon> icons = new ArrayList<>(end - first);
        for (int i = first; i < end; i++) {
            int shown = i - first;
            ItemIcon icon = new ItemIcon(frame.items().get(i), grid.left() + shown % grid.columns() * CELL + inset,
                    grid.top() + shown / grid.columns() * CELL + inset);
            graphics.item(icon.item().stack(), icon.left(), icon.top());
            icons.add(icon);
        }
        Track track = scroll.scrolls() ? drawScrollBar(graphics, grid, scroll) : null;
        return new Drawn(icons, scroll, track);
    }

    private static Track drawScrollBar(GuiGraphicsExtractor graphics, Grid grid, ScrollModel scroll) {
        Track track = new Track(grid.right() + GAP, grid.top(), grid.rowsVisible() * CELL);
        int right = track.left() + BAR_WIDTH;
        graphics.fill(track.left(), track.top(), right, track.top() + track.length(), TRACK);
        int thumbTop = track.top() + scroll.thumbOffset(track.length());
        graphics.fill(track.left(), thumbTop, right, thumbTop + scroll.thumbSpan(track.length()), THUMB);
        return track;
    }

    /**
     * The grid's place in the frame: as many columns as fit inside the margins, centered, under the
     * search bar, with as many rows as fit above the bottom margin.
     *
     * @param columns     the grid's columns
     * @param left        the grid's left edge
     * @param top         the grid's top edge
     * @param rowsVisible the rows the frame shows at once
     */
    record Grid(int columns, int left, int top, int rowsVisible) {

        static Grid of(Frame frame) {
            int count = frame.items().size();
            int columns = Math.clamp((frame.width() - SIDES * MARGIN - GAP - BAR_WIDTH) / CELL, 1,
                    Math.max(1, count));
            int top = MARGIN + SEARCH_HEIGHT + GAP;
            return new Grid(columns, (frame.width() - columns * CELL) / SIDES, top,
                    Math.max(1, (frame.height() - top - MARGIN) / CELL));
        }

        int right() {
            return left + columns * CELL;
        }
    }

    /**
     * Whether a point lies on the scroll bar's track.
     *
     * @param track where the track drew
     * @param x     the point's x
     * @param y     the point's y
     * @return true on the track
     */
    public static boolean onTrack(Track track, double x, double y) {
        return x >= track.left() && x < track.left() + BAR_WIDTH && y >= track.top() && y < track.top() + track.length();
    }

    private static @Nullable ItemIcon itemUnder(List<ItemIcon> icons, double x, double y) {
        for (ItemIcon icon : icons) {
            if (icon.contains(x, y)) {
                return icon;
            }
        }
        return null;
    }

    /**
     * Names the item under the cursor beside it.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param icons    the icons the frame drew
     * @param cursorX  the cursor's x
     * @param cursorY  the cursor's y
     */
    public static void nameWhatTheCursorRestsOn(GuiGraphicsExtractor graphics, Font font, List<ItemIcon> icons,
                                                int cursorX, int cursorY) {
        ItemIcon hovered = itemUnder(icons, cursorX, cursorY);
        if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered.item().stack().getHoverName(), cursorX, cursorY);
        }
    }

    /**
     * The pick a click at a point makes.
     *
     * @param icons the icons the frame drew
     * @param x     the click's x
     * @param y     the click's y
     * @return the pick naming the clicked item, or null for a click on no icon
     */
    public static @Nullable GhostItemPickPayload pickAt(List<ItemIcon> icons, double x, double y) {
        ItemIcon clicked = itemUnder(icons, x, y);
        return clicked == null ? null : new GhostItemPickPayload(clicked.item().id());
    }
}
