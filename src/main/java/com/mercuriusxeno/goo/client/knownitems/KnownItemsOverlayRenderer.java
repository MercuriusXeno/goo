package com.mercuriusxeno.goo.client.knownitems;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws the known items overlay left to right, top to bottom: one darkening fill over the whole frame,
 * the title in the top left corner with a wide bordered search bar beside it, then one item icon per
 * shown item in a grid from the left margin, the hovered cell lighter inside a border, and a scroll bar
 * when the rows overflow; names the item the cursor rests on. The grid's first cell is Cancel, ahead
 * of every item whatever the search shows. The close button is the overlay's widget.
 * decision menu-is-a-darkening-overlay-not-a-screen
 * decision cancel-cell-clears-the-plexer-target
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
    static final int MARGIN = 8;
    /** A span's two sides. */
    private static final int SIDES = 2;
    /** The gap between a cell's edge and its icon. */
    static final int INSET = (CELL - ICON) / SIDES;
    /** The scroll bar's width. */
    static final int BAR_WIDTH = 6;
    /** The search bar's box color, a flat fill a shade off the darkening. */
    static final int SEARCH_BAR = 0xFF202020;
    /** The search text's color. */
    static final int SEARCH_TEXT = 0xFFE0E0E0;
    /** The caret drawn after the typed text. */
    static final String CARET = "_";
    /** The search bar's height. */
    static final int SEARCH_HEIGHT = 16;
    /** The search text's inset from the bar's top-left corner. */
    static final int SEARCH_INSET = 4;
    /** The gap between the search bar and the grid, and between the grid and the scroll bar. */
    static final int GAP = 4;
    /** The scroll bar's track color. */
    static final int TRACK = 0xFF202020;
    /** The scroll bar's thumb color. */
    static final int THUMB = 0xFF808080;
    /** The search bar's border color, light against the darkening so the bar reads as a field. */
    static final int SEARCH_BORDER = 0xFFA0A0A0;
    /** The placeholder's color, dimmer than typed text. */
    static final int HINT_TEXT = 0xFF808080;
    /** The title's color. */
    static final int TITLE_TEXT = 0xFFFFFFFF;
    /** The translation key of the overlay's title. */
    static final String TITLE_KEY = "goo.known_items.title";
    /** The translation key of the empty search bar's placeholder. */
    static final String PLACEHOLDER_KEY = "goo.known_items.search";
    /** The hovered cell's border color. */
    static final int HOVER_BORDER = 0xFFE0E0E0;
    /** The hovered cell's fill, lighter than the darkening around it. */
    static final int HOVER_CELL = 0x60FFFFFF;
    /** The Cancel glyph's red. */
    static final int CANCEL_RED = 0xFFE03030;
    /** The translation key of the Cancel cell's name. */
    static final String CANCEL_KEY = "goo.known_items.cancel";
    /** The Cancel glyph's ring: its outer and inner radius, in pixels, about the icon's center. */
    private static final double CANCEL_OUTER = 7.5;
    private static final double CANCEL_INNER = 5.5;
    /** How far from the diagonal a pixel of the Cancel glyph's slash may sit. */
    private static final double CANCEL_SLASH = 1.0;
    /** A pixel's center, half a pixel in from its corner. */
    private static final double PIXEL_CENTER = 0.5;
    /** A row with no run of red pixels open. */
    private static final int NO_RUN = -1;

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
         * Whether a point lies on the icon's cell, the icon and the gap around it.
         *
         * @param x the point's x
         * @param y the point's y
         * @return true inside the cell
         */
        public boolean contains(double x, double y) {
            return x >= left - INSET && x < left - INSET + CELL && y >= top - INSET && y < top - INSET + CELL;
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
     * @param cursorX  the cursor's x, whose cell draws hovered
     * @param cursorY  the cursor's y
     */
    public record Frame(int width, int height, List<KnownItem> items, String query, int firstRow,
                        int cursorX, int cursorY) {
    }

    /**
     * A square on the frame.
     *
     * @param left the square's left edge
     * @param top  the square's top edge
     * @param size the square's side
     */
    public record Box(int left, int top, int size) {
    }

    /**
     * Where the close button stands: the top right corner, inside the margin.
     *
     * @param width the frame's width
     * @return the button's square
     */
    public static Box closeButton(int width) {
        return new Box(width - MARGIN - SEARCH_HEIGHT, MARGIN, SEARCH_HEIGHT);
    }

    /**
     * What a frame drew that the overlay's input reads back.
     *
     * @param icons  every icon drawn, where it drew
     * @param cancel the Cancel cell's square, null when the scroll hides its row
     * @param scroll the grid's scroll, its first row clamped
     * @param track  where the scroll bar's track drew, null when every row shows
     */
    public record Drawn(List<ItemIcon> icons, @Nullable Box cancel, ScrollModel scroll, @Nullable Track track) {
    }

    /**
     * What a click in the grid picks: an item to target, or nothing to clear the target.
     *
     * @param item the item picked, empty for Cancel
     */
    public record Pick(Optional<Identifier> item) {
        /** The Cancel cell's pick, which clears the target. */
        public static final Pick CLEAR = new Pick(Optional.empty());
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
        // The title marks the overlay as a menu without a panel around it.
        Component title = Component.translatable(TITLE_KEY);
        graphics.text(font, title, MARGIN, MARGIN + SEARCH_INSET, TITLE_TEXT);
        drawSearchBar(graphics, font, MARGIN + font.width(title) + SIDES * GAP,
                closeButton(frame.width()).left() - GAP, frame.query());
        // The Cancel cell takes slot 0, so every item sits one slot on.
        int slots = frame.items().size() + 1;
        ScrollModel scroll = ScrollModel.of((slots + grid.columns() - 1) / grid.columns(), grid.rowsVisible(),
                frame.firstRow());
        int first = scroll.firstRow() * grid.columns();
        int end = Math.min(slots, first + grid.rowsVisible() * grid.columns());
        List<ItemIcon> icons = new ArrayList<>(end - first);
        Box cancel = null;
        for (int slot = first; slot < end; slot++) {
            int shown = slot - first;
            Box cell = new Box(grid.left() + shown % grid.columns() * CELL, grid.top() + shown / grid.columns() * CELL,
                    CELL);
            if (inCell(cell, frame.cursorX(), frame.cursorY())) {
                drawHoveredCell(graphics, cell);
            }
            if (slot == 0) {
                drawCancel(graphics, cell);
                cancel = cell;
            } else {
                ItemIcon icon = new ItemIcon(frame.items().get(slot - 1), cell.left() + INSET, cell.top() + INSET);
                graphics.item(icon.item().stack(), icon.left(), icon.top());
                icons.add(icon);
            }
        }
        Track track = scroll.scrolls() ? drawScrollBar(graphics, grid, scroll) : null;
        return new Drawn(icons, cancel, scroll, track);
    }

    private static boolean inCell(Box cell, double x, double y) {
        return x >= cell.left() && x < cell.left() + cell.size() && y >= cell.top() && y < cell.top() + cell.size();
    }

    /**
     * Draws the Cancel glyph in its cell's icon square: a red ring with a slash from its top left to
     * its bottom right, one fill per run of pixels in each row.
     *
     * @param graphics the GUI graphics extractor
     * @param cell     the Cancel cell
     */
    private static void drawCancel(GuiGraphicsExtractor graphics, Box cell) {
        int left = cell.left() + INSET;
        int top = cell.top() + INSET;
        double center = ICON / (double) SIDES;
        for (int row = 0; row < ICON; row++) {
            int runStart = NO_RUN;
            for (int column = 0; column <= ICON; column++) {
                boolean red = column < ICON
                        && cancelPixel(column + PIXEL_CENTER - center, row + PIXEL_CENTER - center);
                if (red && runStart == NO_RUN) {
                    runStart = column;
                } else if (!red && runStart != NO_RUN) {
                    graphics.fill(left + runStart, top + row, left + column, top + row + 1, CANCEL_RED);
                    runStart = NO_RUN;
                }
            }
        }
    }

    /**
     * Whether a pixel of the icon square, given from its center, is red in the Cancel glyph.
     *
     * @param dx the pixel center's offset right of the icon's center
     * @param dy the pixel center's offset below the icon's center
     * @return true on the ring or on the slash inside it
     */
    static boolean cancelPixel(double dx, double dy) {
        double distance = Math.hypot(dx, dy);
        boolean ring = distance <= CANCEL_OUTER && distance >= CANCEL_INNER;
        boolean slash = distance < CANCEL_INNER && Math.abs(dx - dy) <= CANCEL_SLASH;
        return ring || slash;
    }

    /**
     * Draws the hovered cell lighter inside a one-pixel light border, under its icon.
     *
     * @param graphics the GUI graphics extractor
     * @param cell     the hovered cell
     */
    private static void drawHoveredCell(GuiGraphicsExtractor graphics, Box cell) {
        int left = cell.left();
        int top = cell.top();
        int right = left + CELL;
        int bottom = top + CELL;
        // The border is a ring, so the lighter fill blends over the darkening rather than over the border.
        graphics.fill(left, top, right, top + 1, HOVER_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, HOVER_BORDER);
        graphics.fill(left, top + 1, left + 1, bottom - 1, HOVER_BORDER);
        graphics.fill(right - 1, top + 1, right, bottom - 1, HOVER_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, HOVER_CELL);
    }

    /**
     * Draws the search bar as a dark box inside a one-pixel light border, holding the typed text and a
     * caret, or a dim placeholder while nothing is typed.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font the text draws in
     * @param left     the bar's left edge, inside its border
     * @param right    the bar's right edge, inside its border
     * @param query    the text typed so far
     */
    private static void drawSearchBar(GuiGraphicsExtractor graphics, Font font, int left, int right, String query) {
        int bottom = MARGIN + SEARCH_HEIGHT;
        graphics.fill(left - 1, MARGIN - 1, right + 1, bottom + 1, SEARCH_BORDER);
        graphics.fill(left, MARGIN, right, bottom, SEARCH_BAR);
        int textX = left + SEARCH_INSET;
        int textY = MARGIN + SEARCH_INSET;
        if (query.isEmpty()) {
            graphics.text(font, Component.translatable(PLACEHOLDER_KEY), textX, textY, HINT_TEXT);
        } else {
            graphics.text(font, query + CARET, textX, textY, SEARCH_TEXT);
        }
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
     * The grid's place in the frame: from the left margin under the top row, as many columns as the
     * width holds beside the scroll bar, and as many rows as fit above the bottom margin.
     *
     * @param columns     the grid's columns
     * @param left        the grid's left edge
     * @param top         the grid's top edge
     * @param rowsVisible the rows the frame shows at once
     */
    record Grid(int columns, int left, int top, int rowsVisible) {

        static Grid of(Frame frame) {
            int columns = Math.max(1, (frame.width() - SIDES * MARGIN - GAP - BAR_WIDTH) / CELL);
            int top = MARGIN + SEARCH_HEIGHT + 1 + SIDES * GAP;
            return new Grid(columns, MARGIN, top, Math.max(1, (frame.height() - top - MARGIN) / CELL));
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
     * Names the cell under the cursor beside it: an item by its name, the Cancel cell as Cancel.
     *
     * @param graphics the GUI graphics extractor
     * @param font     the font
     * @param drawn    what the frame drew
     * @param cursorX  the cursor's x
     * @param cursorY  the cursor's y
     */
    public static void nameWhatTheCursorRestsOn(GuiGraphicsExtractor graphics, Font font, Drawn drawn,
                                                int cursorX, int cursorY) {
        if (drawn.cancel() != null && inCell(drawn.cancel(), cursorX, cursorY)) {
            graphics.setTooltipForNextFrame(font, Component.translatable(CANCEL_KEY), cursorX, cursorY);
            return;
        }
        ItemIcon hovered = itemUnder(drawn.icons(), cursorX, cursorY);
        if (hovered != null) {
            graphics.setTooltipForNextFrame(font, hovered.item().stack().getHoverName(), cursorX, cursorY);
        }
    }

    /**
     * The pick a click at a point makes.
     *
     * @param drawn what the frame drew
     * @param x     the click's x
     * @param y     the click's y
     * @return the clicked item's pick, the clear pick for Cancel, or null for a click on no cell
     */
    public static @Nullable Pick pickAt(Drawn drawn, double x, double y) {
        if (drawn.cancel() != null && inCell(drawn.cancel(), x, y)) {
            return Pick.CLEAR;
        }
        ItemIcon clicked = itemUnder(drawn.icons(), x, y);
        return clicked == null ? null : new Pick(Optional.of(clicked.item().id()));
    }
}
