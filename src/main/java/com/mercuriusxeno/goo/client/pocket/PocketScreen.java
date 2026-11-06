package com.mercuriusxeno.goo.client.pocket;

import com.mercuriusxeno.goo.ability.pocket.PocketEntry;
import com.mercuriusxeno.goo.ability.pocket.PocketMenu;
import com.mercuriusxeno.goo.ability.pocket.PocketSearch;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import java.util.List;
import java.util.Locale;

/**
 * Pocket's screen: a search bar over a shelf of six rows, each cell one
 * entry with its count, a scroll bar beside the rows reaching every entry
 * however many the pocket holds, and the player's inventory beneath. The
 * carried stack dropped on the shelf is stored; an entry clicked comes back
 * to the cursor, with shift into the inventory.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public class PocketScreen extends AbstractContainerScreen<PocketMenu> {

    private static final int IMAGE_WIDTH = 184;
    private static final int IMAGE_HEIGHT = 222;
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int CELL = 18;
    private static final int SHELF_LEFT = 8;
    private static final int SHELF_TOP = 18;
    private static final int SHELF_RIGHT = SHELF_LEFT + COLUMNS * CELL;
    private static final int SHELF_BOTTOM = SHELF_TOP + ROWS * CELL;
    private static final int BAR_LEFT = SHELF_RIGHT + 2;
    private static final int BAR_WIDTH = 4;
    private static final int MIN_THUMB = 8;
    private static final int SEARCH_LEFT = 80;
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_WIDTH = 96;
    private static final int SEARCH_HEIGHT = 12;
    private static final int INVENTORY_LABEL_TOP = PocketMenu.INVENTORY_TOP - 11;
    private static final int ITEM_INSET = 1;
    private static final int THOUSAND = 1000;
    private static final int MILLION = 1_000_000;
    private static final int PANEL = 0xFFC6C6C6;
    private static final int PANEL_EDGE = 0xFF555555;
    private static final int CELL_SHADE = 0xFF8B8B8B;
    private static final int CELL_HOVER = 0x80FFFFFF;
    private static final int TRACK = 0xFF555555;
    private static final int THUMB = 0xFFEEEEEE;
    private static final String SEARCH_HINT = "goo.pocket.search";
    private static final String NO_SEARCH = "";
    private static final String THOUSANDS = "%.1fk";
    private static final String MILLIONS = "%.1fM";
    private static final int HOVER_SIZE = 16;
    private static final int OFF_SHELF = -1;

    private EditBox search;
    private int firstRow;
    private boolean draggingBar;

    /**
     * @param menu      the pocket's menu
     * @param inventory the player's inventory
     * @param title     the pocket's title
     */
    public PocketScreen(PocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IMAGE_WIDTH, IMAGE_HEIGHT);
        inventoryLabelY = INVENTORY_LABEL_TOP;
    }

    @Override
    protected void init() {
        super.init();
        search = new EditBox(font, leftPos + SEARCH_LEFT, topPos + SEARCH_TOP, SEARCH_WIDTH, SEARCH_HEIGHT,
                Component.translatable(SEARCH_HINT));
        search.setHint(Component.translatable(SEARCH_HINT));
        search.setResponder(text -> firstRow = 0);
        addRenderableWidget(search);
    }

    /**
     * The shelf indices the search leaves standing.
     *
     * @return the entries shown, in shelf order
     */
    private List<Integer> shown() {
        List<String> names = menu.entries().stream().map(entry -> entry.kind().getHoverName().getString()).toList();
        return PocketSearch.visible(names, search == null ? NO_SEARCH : search.getValue());
    }

    private int lastFirstRow(int shownCount) {
        int rows = (shownCount + COLUMNS - 1) / COLUMNS;
        return Math.max(0, rows - ROWS);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(leftPos - 1, topPos - 1, leftPos + imageWidth + 1, topPos + imageHeight + 1, PANEL_EDGE);
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                shadeCell(graphics, leftPos + SHELF_LEFT + column * CELL, topPos + SHELF_TOP + row * CELL);
            }
        }
        for (Slot slot : menu.slots) {
            shadeCell(graphics, leftPos + slot.x - ITEM_INSET, topPos + slot.y - ITEM_INSET);
        }
    }

    private static void shadeCell(GuiGraphicsExtractor graphics, int left, int top) {
        graphics.fill(left, top, left + CELL - ITEM_INSET, top + CELL - ITEM_INSET, CELL_SHADE);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick);
        List<Integer> shown = shown();
        firstRow = Math.clamp(firstRow, 0, lastFirstRow(shown.size()));
        List<PocketEntry> entries = menu.entries();
        int hovered = cellUnder(mouseX, mouseY);
        for (int cell = 0; cell < ROWS * COLUMNS; cell++) {
            int place = firstRow * COLUMNS + cell;
            if (place >= shown.size()) {
                break;
            }
            PocketEntry entry = entries.get(shown.get(place));
            int left = leftPos + SHELF_LEFT + (cell % COLUMNS) * CELL;
            int top = topPos + SHELF_TOP + (cell / COLUMNS) * CELL;
            graphics.item(entry.kind(), left, top);
            graphics.itemDecorations(font, entry.kind(), left, top, compactCount(entry.count()));
            if (cell == hovered) {
                graphics.fill(left, top, left + HOVER_SIZE, top + HOVER_SIZE, CELL_HOVER);
                graphics.setTooltipForNextFrame(font, entry.kind(), mouseX, mouseY);
            }
        }
        drawBar(graphics, shown.size());
    }

    private void drawBar(GuiGraphicsExtractor graphics, int shownCount) {
        int left = leftPos + BAR_LEFT;
        int top = topPos + SHELF_TOP;
        int height = ROWS * CELL;
        graphics.fill(left, top, left + BAR_WIDTH, top + height, TRACK);
        int last = lastFirstRow(shownCount);
        int rows = Math.max(ROWS, (shownCount + COLUMNS - 1) / COLUMNS);
        int thumbHeight = Math.max(MIN_THUMB, height * ROWS / rows);
        int thumbTop = last == 0 ? top : top + (height - thumbHeight) * firstRow / last;
        graphics.fill(left, thumbTop, left + BAR_WIDTH, thumbTop + thumbHeight, THUMB);
    }

    /**
     * A count short enough for a cell: 999, then 1.2k, then 3.4M.
     *
     * @param count the entry's count
     * @return the label
     */
    static String compactCount(long count) {
        if (count < THOUSAND) {
            return Long.toString(count);
        }
        if (count < MILLION) {
            return String.format(Locale.ROOT, THOUSANDS, count / (double) THOUSAND);
        }
        return String.format(Locale.ROOT, MILLIONS, count / (double) MILLION);
    }

    /**
     * The shelf cell under the cursor.
     *
     * @param x the cursor's x
     * @param y the cursor's y
     * @return the cell's place among the cells shown, or OFF_SHELF off the shelf
     */
    private int cellUnder(double x, double y) {
        double shelfX = x - leftPos - SHELF_LEFT;
        double shelfY = y - topPos - SHELF_TOP;
        if (shelfX < 0 || shelfY < 0 || shelfX >= COLUMNS * CELL || shelfY >= ROWS * CELL) {
            return OFF_SHELF;
        }
        return (int) (shelfY / CELL) * COLUMNS + (int) (shelfX / CELL);
    }

    private boolean onBar(double x, double y) {
        double barX = x - leftPos - BAR_LEFT;
        double barY = y - topPos - SHELF_TOP;
        return barX >= 0 && barX < BAR_WIDTH && barY >= 0 && barY < ROWS * CELL;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (onBar(event.x(), event.y())) {
            draggingBar = true;
            scrollToCursor(event.y());
            return true;
        }
        int cell = cellUnder(event.x(), event.y());
        if (cell < 0) {
            return super.mouseClicked(event, doubleClick);
        }
        if (!menu.getCarried().isEmpty()) {
            pressButton(PocketMenu.STORE_CARRIED);
            return true;
        }
        List<Integer> shown = shown();
        int place = firstRow * COLUMNS + cell;
        if (place < shown.size()) {
            pressButton(PocketMenu.takeButton(shown.get(place), event.hasShiftDown()));
        }
        return true;
    }

    private void pressButton(int buttonId) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingBar) {
            scrollToCursor(event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingBar = false;
        return super.mouseReleased(event);
    }

    private void scrollToCursor(double y) {
        int last = lastFirstRow(shown().size());
        double share = (y - topPos - SHELF_TOP) / (ROWS * CELL);
        firstRow = Math.clamp(Math.round(share * last), 0, last);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (cellUnder(x, y) >= 0 || onBar(x, y)) {
            firstRow = Math.clamp(firstRow - (int) Math.signum(scrollY), 0, lastFirstRow(shown().size()));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (search.isFocused() && !event.isEscape()) {
            return search.keyPressed(event) || search.canConsumeInput();
        }
        return super.keyPressed(event);
    }

}
