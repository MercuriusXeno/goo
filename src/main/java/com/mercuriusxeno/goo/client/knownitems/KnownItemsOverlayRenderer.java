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
     * @param items  the known items in the order they draw
     */
    public record Frame(int width, int height, List<KnownItem> items) {
    }

    /**
     * Draws the frame.
     *
     * @param graphics the GUI graphics extractor
     * @param frame    what the frame draws
     * @return every icon drawn, where it drew
     */
    public static List<ItemIcon> render(GuiGraphicsExtractor graphics, Frame frame) {
        graphics.fill(0, 0, frame.width(), frame.height(), DARKEN);
        List<ItemIcon> icons = layout(frame);
        for (ItemIcon icon : icons) {
            graphics.item(icon.item().stack(), icon.left(), icon.top());
        }
        return icons;
    }

    /**
     * Lays the items out in rows as wide as the frame allows inside its margins, the grid centered.
     *
     * @param frame what the frame draws
     * @return each item's icon, in the items' order
     */
    static List<ItemIcon> layout(Frame frame) {
        int count = frame.items().size();
        int columns = Math.clamp((frame.width() - SIDES * MARGIN) / CELL, 1, Math.max(1, count));
        int rows = (count + columns - 1) / columns;
        int gridLeft = (frame.width() - columns * CELL) / SIDES;
        int gridTop = Math.max(MARGIN, (frame.height() - rows * CELL) / SIDES);
        int inset = (CELL - ICON) / SIDES;
        List<ItemIcon> icons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            icons.add(new ItemIcon(frame.items().get(i), gridLeft + i % columns * CELL + inset,
                    gridTop + i / columns * CELL + inset));
        }
        return icons;
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
