package com.mercuriusxeno.goo.client.pocket;

import com.mercuriusxeno.goo.ability.pocket.PocketEntry;
import com.mercuriusxeno.goo.ability.pocket.PocketMenu;
import com.mercuriusxeno.goo.ability.pocket.PocketReserve;
import com.mercuriusxeno.goo.ability.pocket.PocketSearch;
import com.mercuriusxeno.goo.item.GooFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import java.util.List;
import java.util.Locale;

/**
 * Pocket's screen, drawn as vanilla draws a chest: the void the pocket holds
 * back where a title would stand, a search bar beside it, a shelf of six rows
 * of slots, each cell one entry with its count, the creative inventory's
 * scroller beside the rows reaching every entry however many the pocket
 * holds, and the player's inventory beneath. The carried stack dropped on the
 * shelf is stored; an entry clicked comes back to the cursor, with shift into
 * the inventory.
 * decision pocket-is-a-void-reserving-container-with-scroll-and-search
 */
public class PocketScreen extends AbstractContainerScreen<PocketMenu> {

    private static final int IMAGE_WIDTH = 196;
    private static final int IMAGE_HEIGHT = 222;
    private static final int COLUMNS = 9;
    private static final int ROWS = 6;
    private static final int CELL = 18;
    private static final int SHELF_LEFT = 8;
    private static final int SHELF_TOP = 18;
    private static final int SHELF_RIGHT = SHELF_LEFT + COLUMNS * CELL;
    private static final int BAR_LEFT = SHELF_RIGHT + 4;
    private static final int BAR_WIDTH = 14;
    private static final int SCROLLER_WIDTH = 12;
    private static final int SCROLLER_HEIGHT = 15;
    /** The track's inset edge above and below the scroller. */
    private static final int TRACK_EDGES = 2;
    private static final int SEARCH_LEFT = 100;
    private static final int SEARCH_TOP = 4;
    private static final int SEARCH_WIDTH = 88;
    private static final int SEARCH_HEIGHT = 12;
    private static final int INVENTORY_LABEL_TOP = PocketMenu.INVENTORY_TOP - 11;
    private static final int ITEM_INSET = 1;
    private static final int THOUSAND = 1000;
    private static final int MILLION = 1_000_000;
    private static final int CELL_HOVER = 0x80FFFFFF;
    /** Vanilla's label grey. */
    private static final int LABEL = 0xFF404040;
    private static final Identifier PANEL = Identifier.fromNamespaceAndPath("goo", "pocket/background");
    private static final Identifier TRACK = Identifier.fromNamespaceAndPath("goo", "pocket/scroll_track");
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_IDLE =
            Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final String SEARCH_HINT = "goo.pocket.search";
    private static final String VOID_HELD = "goo.pocket.void_held";
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
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, leftPos, topPos, imageWidth, imageHeight);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                drawSlot(graphics, leftPos + SHELF_LEFT + column * CELL, topPos + SHELF_TOP + row * CELL);
            }
        }
        for (Slot slot : menu.slots) {
            drawSlot(graphics, leftPos + slot.x, topPos + slot.y);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TRACK, leftPos + BAR_LEFT, topPos + SHELF_TOP - ITEM_INSET,
                BAR_WIDTH, ROWS * CELL);
    }

    /**
     * Draws vanilla's slot around an item cell.
     *
     * @param graphics the frame's graphics
     * @param itemLeft the cell's item's left
     * @param itemTop  the cell's item's top
     */
    private static void drawSlot(GuiGraphicsExtractor graphics, int itemLeft, int itemTop) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, itemLeft - ITEM_INSET, itemTop - ITEM_INSET, CELL,
                CELL);
    }

    /**
     * The void the pocket holds back in place of a title, and the inventory's label.
     */
    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        long held = PocketReserve.voidHeldBy(menu.entries());
        graphics.text(font, Component.translatable(VOID_HELD, GooFormat.formatAmount(held)), titleLabelX,
                titleLabelY, LABEL, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL, false);
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
        int left = leftPos + BAR_LEFT + ITEM_INSET;
        int top = topPos + SHELF_TOP;
        int travel = ROWS * CELL - SCROLLER_HEIGHT - TRACK_EDGES;
        int last = lastFirstRow(shownCount);
        int scrollerTop = last == 0 ? top : top + travel * firstRow / last;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, last == 0 ? SCROLLER_IDLE : SCROLLER, left, scrollerTop,
                SCROLLER_WIDTH, SCROLLER_HEIGHT);
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
