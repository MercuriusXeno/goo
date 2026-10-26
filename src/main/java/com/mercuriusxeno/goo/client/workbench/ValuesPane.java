package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the Values tab: the Items, Tags and Uncovered chips, the search box, each entry with its icon and where its
 * value comes from, and the selected entry's expression and resolved values, each list scrolling under the wheel, as
 * index.html's values section does (decision goo-workbench-opens-the-in-game-manager-screen); the list and the detail
 * each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ValuesTab}.
 */
final class ValuesPane {
    private static final int SIDEBAR_WIDTH = 180;
    private static final int PANE_GAP = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    private static final int ROW_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    private static final int CHIP_GAP = 8;
    private static final int GAP = 3;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int GOLD = 0xFFE0C060;
    private static final int CHIP_OFF = 0xFF808080;
    private static final int SELECTED_ROW_COLOR = 0x60FFFFFF;
    private static final String ITEMS_CHIP = "Items";
    private static final String TAGS_CHIP = "Tags";
    private static final String UNCOVERED_CHIP = "Uncovered ";
    private static final String SEARCH_HINT = "Search items and tags";
    private static final String EXPRESSION_LABEL = "Value: click it to build";
    private static final String NO_EXPRESSION = "(no entry)";
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final int LINK = 0xFF80C0FF;
    private static final String NO_SELECTION = "Select an item or tag to read and edit its entry.";
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String DENIED_LINE = "denied";
    private static final String NO_VALUE_LINE = "no value: nothing in the file or the recipes values it";
    private static final String NO_TEXT = "";

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final ScrollBar listBar = new ScrollBar(1);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private boolean visible;

    ValuesPane(ValuesTab values, ItemIcons icons, ManagerActions actions, ExpressionBuilderPane builder) {
        this.values = values;
        this.icons = icons;
        this.actions = actions;
        this.builder = builder;
    }

    /**
     * @param font the screen's font
     */
    void init(Font font) {
        String searched = search == null ? NO_TEXT : search.getValue();
        search = actions.addWidget(new EditBox(font, 0, 0, SIDEBAR_WIDTH, BOX_HEIGHT, Component.literal(SEARCH_HINT)));
        search.setHint(Component.literal(SEARCH_HINT));
        search.setValue(searched);
        search.setResponder(text -> {
            values.search(text);
            listBar.reset();
        });
        builder.init(font);
        show(visible);
    }

    /**
     * @param shown whether the Values tab is the one drawn
     */
    void show(boolean shown) {
        visible = shown;
        if (search != null) {
            search.visible = shown;
        }
        builder.show(shown);
    }

    /**
     * @param key the GLFW key pressed
     * @return whether the builder took the key
     */
    boolean keyPressed(int key) {
        return builder.keyPressed(key);
    }

    /**
     * @param x      the cursor's x
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, int left, double amount) {
        if (x < left + SIDEBAR_WIDTH) {
            listBar.wheel(amount);
        } else {
            detailBar.wheel(amount);
        }
    }

    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        int y = drawChips(graphics, font, bounds.left(), bounds.top(), targets);
        if (search != null) {
            search.setPosition(bounds.left(), y);
        }
        drawEntries(graphics, font, new GroupsPane.Bounds(bounds.left(), y + BOX_HEIGHT + GAP,
                bounds.left() + SIDEBAR_WIDTH, bounds.bottom()), targets);
        drawSelected(graphics, font, new GroupsPane.Bounds(bounds.left() + SIDEBAR_WIDTH + PANE_GAP, bounds.top(),
                bounds.right(), bounds.bottom()), targets);
    }

    // Answers the y below the chips.
    private int drawChips(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        int x = left;
        x = drawChip(graphics, font, ITEMS_CHIP, ValuesTab.Filter.ITEMS, x, top, WHITE, targets);
        x = drawChip(graphics, font, TAGS_CHIP, ValuesTab.Filter.TAGS, x, top, WHITE, targets);
        int uncovered = values.uncoveredCount();
        drawChip(graphics, font, UNCOVERED_CHIP + uncovered, ValuesTab.Filter.UNCOVERED, x, top,
                uncovered == 0 ? WHITE : RED, targets);
        return top + LINE_HEIGHT + GAP;
    }

    // Answers the x past the chip; the chip the filter stands on draws in its color, the others dim.
    private int drawChip(GuiGraphicsExtractor graphics, Font font, String label, ValuesTab.Filter filter, int x,
                         int y, int color, ClickTargets targets) {
        boolean on = values.filter() == filter;
        graphics.text(font, label, x, y, on ? color : CHIP_OFF);
        if (on) {
            graphics.fill(x, y + LINE_HEIGHT - 1, x + font.width(label), y + LINE_HEIGHT, color);
        }
        targets.add(x, y, font.width(label), LINE_HEIGHT, () -> {
            values.filter(filter);
            listBar.reset();
        });
        return x + font.width(label) + CHIP_GAP;
    }

    private void drawEntries(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds list, ClickTargets targets) {
        List<ValuesTab.ManagerEntry> listed = values.listed();
        if (listed.isEmpty()) {
            graphics.text(font, NO_ENTRIES, list.left(), list.top(), GRAY);
            return;
        }
        int shown = Math.max(1, (list.bottom() - list.top()) / ROW_HEIGHT);
        listBar.measure(listed.size(), shown);
        int firstShown = listBar.offset();
        Optional<String> selectedId = values.selectedRow().map(ValuesTab.EntryRow::id);
        int textX = list.left() + ICON_SIZE + ICON_GAP;
        int rowRight = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        int textWidth = rowRight - textX;
        int y = list.top();
        for (int index = firstShown; index < Math.min(listed.size(), firstShown + shown); index++) {
            ValuesTab.ManagerEntry entry = listed.get(index);
            if (selectedId.filter(entry.id()::equals).isPresent()) {
                graphics.fill(list.left(), y, rowRight, y + ROW_HEIGHT, SELECTED_ROW_COLOR);
            }
            graphics.item(icons.of(entry.id()), list.left(), y + HALF);
            String first = entry.tag() ? entry.id() : entry.name();
            graphics.text(font, font.plainSubstrByWidth(first, textWidth), textX, y + 1, WHITE);
            if (entry.tag()) {
                graphics.text(font, font.plainSubstrByWidth(entry.name(), textWidth), textX, y + LINE_HEIGHT, GRAY);
            } else {
                ValuesTab.Source source = values.sourceOf(entry.id());
                graphics.text(font, font.plainSubstrByWidth(source.label(), textWidth), textX, y + LINE_HEIGHT,
                        colorOf(source.kind()));
            }
            targets.add(list.left(), y, rowRight - list.left(), ROW_HEIGHT, () -> {
                values.select(entry.id());
                detailBar.reset();
                builder.close();
            });
            y += ROW_HEIGHT;
        }
        listBar.draw(graphics, targets, list.right(), list.top(), list.top() + shown * ROW_HEIGHT);
    }

    private static int colorOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> GREEN;
            case GROUP -> GOLD;
            case DENIED -> GRAY;
            case NONE -> RED;
        };
    }

    private void drawSelected(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds pane,
                              ClickTargets targets) {
        Optional<ValuesTab.EntryRow> selected = values.selectedRow();
        int x = pane.left();
        int width = pane.right() - x;
        if (selected.isEmpty()) {
            graphics.text(font, NO_SELECTION, x, pane.top(), GRAY);
            return;
        }
        ValuesTab.EntryRow row = selected.get();
        drawHeading(graphics, font, row, pane);
        int y = pane.top() + LINE_HEIGHT * HALF + GAP;
        graphics.text(font, EXPRESSION_LABEL, x, y, GRAY);
        y += LINE_HEIGHT;
        y += builder.isOpen() ? builder.extract(graphics, font, pane, y, targets)
                : drawValueView(graphics, font, row, x, y, width, targets);
        drawAmounts(graphics, font, row, new GroupsPane.Bounds(x, y + GAP, pane.right(), pane.bottom()), targets);
    }

    // The entry's value curated, a click on it opening the builder over it
    // (decision expression-builder-composes-every-form-without-typing); answers the height it took.
    private int drawValueView(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, int x, int y,
                              int width, ClickTargets targets) {
        List<ExpressionLines.Run> runs = row.expression().isBlank() ? ExpressionLines.textRuns(NO_EXPRESSION)
                : ExpressionLines.shownRuns(row.expression(), icons::nameOf);
        int height = ExpressionLines.height(font, runs, width);
        graphics.fill(x, y, x + width, y + height, READ_VIEW_COLOR);
        targets.add(x, y, width, height, () -> builder.open(row.expression(), typed -> {
            values.edit(typed);
            actions.markUnsaved();
        }, actions::draftChanged));
        ExpressionLines.draw(font, runs, x, y, width, new ExpressionLines.Painter(graphics, icons, targets, WHITE,
                LINK, (name, linkX, linkY, linkWidth) -> { }));
        return height;
    }

    // The selected entry's icon, name and id, and where its value comes from.
    private void drawHeading(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, GroupsPane.Bounds pane) {
        graphics.item(icons.of(row.id()), pane.left(), pane.top());
        int textX = pane.left() + ICON_SIZE + ICON_GAP;
        String heading = row.id().startsWith(ValuesTab.TAG_MARK) ? row.id() : row.name();
        graphics.text(font, font.plainSubstrByWidth(heading, pane.right() - textX), textX, pane.top(), WHITE);
        if (!row.id().startsWith(ValuesTab.TAG_MARK)) {
            ValuesTab.Source source = values.sourceOf(row.id());
            graphics.text(font, source.label(), textX, pane.top() + LINE_HEIGHT, colorOf(source.kind()));
        }
    }

    // Each type draws as its icon beside its amount, a line each (decision goo-types-draw-as-icons-named-on-hover);
    // answers the y below them.
    private int drawAmountLines(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row,
                                GroupsPane.Bounds area, int top, ClickTargets targets) {
        int width = area.right() - area.left() - ScrollBar.WIDTH - ScrollBar.GAP;
        int y = top;
        targets.clip(area.left(), area.top(), area.right(), area.bottom());
        for (Map.Entry<String, String> amount : row.amounts().entrySet()) {
            ExpressionLines.draw(font, ExpressionLines.amountRuns(Map.of(amount.getKey(), amount.getValue())),
                    area.left(), y, width, new ExpressionLines.Painter(graphics, icons, targets, WHITE, WHITE,
                            (name, linkX, linkY, linkWidth) -> { }));
            y += ExpressionLines.ICON_LINE_HEIGHT;
        }
        targets.unclip();
        return y;
    }

    private void drawAmounts(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row,
                             GroupsPane.Bounds area, ClickTargets targets) {
        int textLines = (row.denied() || row.amounts().isEmpty() ? 1 : 0) + (row.error().isEmpty() ? 0 : 1);
        detailBar.measure(textLines * LINE_HEIGHT + row.amounts().size() * ExpressionLines.ICON_LINE_HEIGHT,
                area.bottom() - area.top());
        int width = area.right() - area.left() - ScrollBar.WIDTH - ScrollBar.GAP;
        graphics.enableScissor(area.left(), area.top(), area.right(), area.bottom());
        int y = area.top() - detailBar.offset();
        if (row.denied()) {
            graphics.text(font, DENIED_LINE, area.left(), y, GRAY);
            y += LINE_HEIGHT;
        } else if (row.amounts().isEmpty()) {
            graphics.text(font, NO_VALUE_LINE, area.left(), y, RED);
            y += LINE_HEIGHT;
        }
        y = drawAmountLines(graphics, font, row, area, y, targets);
        if (!row.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(row.error(), width), area.left(), y, RED);
        }
        graphics.disableScissor();
        detailBar.draw(graphics, targets, area.right(), area.top(), area.bottom());
    }
}
