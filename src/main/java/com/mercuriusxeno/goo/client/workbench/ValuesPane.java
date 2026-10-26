package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Draws the Values tab: the Items, Tags and Uncovered set on a line of its own, the search box, each entry with its icon and where its
 * value comes from, and the selected entry's expression and resolved values, each list scrolling under the wheel, as
 * index.html's values section does (decision goo-workbench-opens-the-in-game-manager-screen); the list and the detail
 * each draw a {@link ScrollBar} (decision every-tab-draws-a-scroll-bar). The state lives in {@link ValuesTab}.
 */
final class ValuesPane {
    private static final int SIDEBAR_WIDTH = 180;
    private static final int PANE_GAP = 12;
    private static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    /** An Items row's height, its sprite's and margin. */
    static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;
    // An 18px row's second text line, clear of the separator under it.
    private static final int SECOND_LINE = 9;
    /** The room between the filter set and the search box below it. */
    static final int CHIP_LINE_GAP = 3;
    private static final int CHIP_TEXT_NUDGE = 3;
    private static final int GAP = 3;
    private static final int HALF = 2;
    private static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;
    /** The color of no value, the one red source. */
    static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF80E080;
    private static final int DERIVED_COLOR = 0xFFA0C8F0;
    /** The room between two runs of an Items row's value. */
    static final int RUN_GAP = 3;
    private static final int RUN_ICON_GAP = 2;
    private static final int RUN_TEXT_NUDGE = 5;
    // The name and source column beside an Items row's icon; the value takes the rest of the row.
    private static final int NAME_COLUMN = 70;
    private static final int GOLD = 0xFFE0C060;
    private static final int CHIP_OFF = 0xFF808080;
    private static final int CHIP_HOLLOW = 0xFF202020;
    private static final int CHIP_FILLED_TEXT = 0xFF000000;
    private static final int SELECTED_ROW_COLOR = 0x60FFFFFF;
    private static final String ITEMS_CHIP = "Items";
    private static final String TAGS_CHIP = "Tags";
    private static final String UNCOVERED_CHIP = "Uncovered ";
    private static final String SEARCH_HINT = "Search items and tags";
    private static final String EXPRESSION_LABEL = "Value";
    private static final String NO_EXPRESSION = "(no entry)";
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String NO_SELECTION = "Select an item or tag to read and edit its entry.";
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String DENIED_LINE = "denied";
    private static final String NO_VALUE_LINE = "no value: nothing in the file or the recipes values it";
    private static final String NO_TEXT = "";
    private static final int NO_REVEAL = -1;

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final ScrollBar listBar = new ScrollBar(1);
    private final ScrollBar detailBar = new ScrollBar(SCROLL_STEP);
    private boolean visible;
    private int revealRow = NO_REVEAL;

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
     * Selects an item under the Items filter, the search cleared, and scrolls the list to it on the next frame
     * (decision used-by-icons-link-to-items-and-hover-their-value).
     *
     * @param id the item's id
     */
    void reveal(String id) {
        int index = values.open(id);
        if (search != null) {
            search.setValue(NO_TEXT);
        }
        builder.close();
        detailBar.reset();
        revealRow = index;
    }

    /**
     * @param x      the cursor's x
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, int left, double amount) {
        if (builder.scrollList(amount)) {
            return;
        }
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
        builder.drawOverlays(graphics, font, targets);
    }

    /**
     * One box of the Items, Tags and Uncovered set.
     *
     * @param filter what clicking it lists
     * @param label  its label
     * @param left   its left edge
     * @param top    its top edge
     * @param width  its width
     * @param height its height
     * @param filled whether it is the filter the tab holds
     */
    record Chip(ValuesTab.Filter filter, String label, int left, int top, int width, int height, boolean filled) {
    }

    /**
     * @param chips     the set's boxes, left to right
     * @param searchTop the search box's top, one line below them
     */
    record ChipLayout(List<Chip> chips, int searchTop) {
    }

    /**
     * Lays the filters as one segmented set on a line of their own: adjoining boxes sharing their inner borders,
     * the one the tab holds filled (decision items-filters-sit-on-their-own-line-as-one-set).
     *
     * @param labels   the Items, Tags and Uncovered labels, in that order
     * @param width    a label's width
     * @param left     the set's left edge
     * @param top      the set's top edge
     * @param selected the filter the tab holds
     * @return the boxes and the search box's top below them
     */
    static ChipLayout layChips(List<String> labels, ToIntFunction<String> width, int left, int top,
                               ValuesTab.Filter selected) {
        List<Chip> chips = new ArrayList<>();
        int x = left;
        for (int index = 0; index < labels.size(); index++) {
            ValuesTab.Filter filter = ValuesTab.Filter.values()[index];
            String label = labels.get(index);
            int chipWidth = width.applyAsInt(label) + ButtonPainter.PADDING + ButtonPainter.PADDING;
            chips.add(new Chip(filter, label, x, top, chipWidth, ButtonPainter.HEIGHT, filter == selected));
            x += chipWidth - 1;
        }
        return new ChipLayout(chips, top + ButtonPainter.HEIGHT + CHIP_LINE_GAP);
    }

    // Answers the search box's top below the set.
    private int drawChips(GuiGraphicsExtractor graphics, Font font, int left, int top, ClickTargets targets) {
        int uncovered = values.uncoveredCount();
        ChipLayout layout = layChips(List.of(ITEMS_CHIP, TAGS_CHIP, UNCOVERED_CHIP + uncovered), font::width, left,
                top, values.filter());
        for (Chip chip : layout.chips()) {
            drawChip(graphics, font, chip, chip.filter() == ValuesTab.Filter.UNCOVERED && uncovered > 0 ? RED : WHITE,
                    targets);
        }
        return layout.searchTop();
    }

    // The filled box reads its label dark on its color; a hollow one reads its label in the color, dimmed.
    private void drawChip(GuiGraphicsExtractor graphics, Font font, Chip chip, int color, ClickTargets targets) {
        int right = chip.left() + chip.width();
        int bottom = chip.top() + chip.height();
        graphics.fill(chip.left(), chip.top(), right, bottom, chip.filled() ? color : CHIP_OFF);
        graphics.fill(chip.left() + 1, chip.top() + 1, right - 1, bottom - 1, chip.filled() ? color : CHIP_HOLLOW);
        graphics.text(font, chip.label(), chip.left() + ButtonPainter.PADDING, chip.top() + CHIP_TEXT_NUDGE,
                chip.filled() ? CHIP_FILLED_TEXT : color);
        targets.add(chip.left(), chip.top(), chip.width(), chip.height(), () -> {
            values.filter(chip.filter());
            listBar.reset();
        });
    }

    private void drawEntries(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds list, ClickTargets targets) {
        List<ValuesTab.ManagerEntry> listed = values.listed();
        if (listed.isEmpty()) {
            graphics.text(font, NO_ENTRIES, list.left(), list.top(), GRAY);
            return;
        }
        scrollToRevealed();
        int firstShown = Math.min(listBar.offset(), listed.size() - 1);
        graphics.enableScissor(list.left(), list.top(), list.right(), list.bottom());
        targets.clip(list.left(), list.top(), list.right(), list.bottom());
        int whole = drawRowsFrom(graphics, font, listed, firstShown, list, targets);
        targets.unclip();
        graphics.disableScissor();
        listBar.measure(listed.size(), Math.max(1, whole));
        listBar.draw(graphics, targets, list.right(), list.top(), list.bottom());
    }

    // Draws rows from the first shown until the list's bottom; answers how many stood whole inside it.
    private int drawRowsFrom(GuiGraphicsExtractor graphics, Font font, List<ValuesTab.ManagerEntry> listed,
                             int firstShown, GroupsPane.Bounds list, ClickTargets targets) {
        Optional<String> selectedId = values.selectedRow().map(ValuesTab.EntryRow::id);
        int right = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        int y = list.top();
        int whole = 0;
        for (int index = firstShown; index < listed.size() && y < list.bottom(); index++) {
            ValuesTab.ManagerEntry entry = listed.get(index);
            RowLayout.drawSeparatorAbove(graphics, index - firstShown, y, list.left(), right);
            y += drawEntry(graphics, font, entry, new GroupsPane.Bounds(list.left(), y, right, list.bottom()),
                    selectedId.filter(entry.id()::equals).isPresent(), targets);
            whole += y <= list.bottom() ? 1 : 0;
        }
        return whole;
    }

    // One Items row: the icon, the name over its source, and the item's goo value inline beside them, wrapped onto
    // as many 18px rows as it needs, then a derived item's "derived from" and its inputs on one line; answers its height
    // (decision items-rows-read-valued-derived-or-no-value-with-values-inline).
    private int drawEntry(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, boolean selected, ClickTargets targets) {
        int textX = row.left() + ICON_SIZE + ICON_GAP;
        int runsX = textX + NAME_COLUMN + GAP;
        Map<String, String> worth = entry.tag() ? Map.of() : values.worthOf(entry.id());
        List<String> from = entry.tag() ? List.of() : values.derivedFrom(entry.id());
        ValueRuns runs = layValueRuns(worth.values().stream().map(amount -> runWidth(font, amount)).toList(),
                row.right() - runsX);
        int height = (runs.rows() + (from.isEmpty() ? 0 : 1)) * ROW_HEIGHT;
        if (selected) {
            graphics.fill(row.left(), row.top(), row.right(), row.top() + height, SELECTED_ROW_COLOR);
        }
        targets.add(row.left(), row.top(), row.right() - row.left(), height, () -> {
            values.select(entry.id());
            detailBar.reset();
            builder.close();
        });
        drawEntryText(graphics, font, entry, textX, row.top());
        drawValueRuns(graphics, font, worth, runs, runsX, row.top(), targets);
        drawDerivedFrom(graphics, font, from, textX, row.top() + runs.rows() * ROW_HEIGHT, targets);
        return height;
    }

    private void drawEntryText(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry, int textX,
                               int top) {
        graphics.item(icons.of(entry.id()), textX - ICON_SIZE - ICON_GAP, top + 1);
        graphics.text(font, font.plainSubstrByWidth(entry.tag() ? entry.id() : entry.name(), NAME_COLUMN), textX,
                top + 1, WHITE);
        if (entry.tag()) {
            graphics.text(font, font.plainSubstrByWidth(entry.name(), NAME_COLUMN), textX, top + SECOND_LINE, GRAY);
            return;
        }
        ValuesTab.Source source = values.sourceOf(entry.id());
        if (source.kind() == ValuesTab.SourceKind.DERIVED && !values.derivedFrom(entry.id()).isEmpty()) {
            return;
        }
        graphics.text(font, font.plainSubstrByWidth(source.label(), NAME_COLUMN), textX, top + SECOND_LINE,
                colorOf(source.kind()));
    }

    private static void drawValueRuns(GuiGraphicsExtractor graphics, Font font, Map<String, String> worth,
                                      ValueRuns runs, int left, int top, ClickTargets targets) {
        Iterator<Spot> spots = runs.spots().iterator();
        for (Map.Entry<String, String> amount : worth.entrySet()) {
            Spot spot = spots.next();
            int x = left + spot.x();
            int y = top + spot.row() * ROW_HEIGHT;
            GooTypeIcons.draw(graphics, targets, amount.getKey(), x, y + 1);
            graphics.text(font, amount.getValue(), x + ICON_SIZE + RUN_ICON_GAP, y + RUN_TEXT_NUDGE, WHITE);
        }
    }

    // "derived from" and the inputs' icons on one line, each icon a link to its own row, so the words read joined
    // to the items they name.
    private void drawDerivedFrom(GuiGraphicsExtractor graphics, Font font, List<String> from, int left, int top,
                                 ClickTargets targets) {
        if (from.isEmpty()) {
            return;
        }
        String words = ValuesTab.DERIVED_SOURCE;
        graphics.text(font, words, left, top + RUN_TEXT_NUDGE, colorOf(ValuesTab.SourceKind.DERIVED));
        int iconsLeft = left + font.width(words) + GAP;
        for (int index = 0; index < from.size(); index++) {
            int x = iconsLeft + index * (ICON_SIZE + HALF);
            graphics.item(icons.of(from.get(index)), x, top + 1);
            ItemLinks.add(targets, actions, from.get(index), x, top + 1);
        }
    }

    private static int runWidth(Font font, String amount) {
        return ICON_SIZE + RUN_ICON_GAP + font.width(amount);
    }

    /**
     * Where one goo type's run stands in an Items row's value.
     *
     * @param x   its offset from the value's left edge
     * @param row its row, from 0
     */
    record Spot(int x, int row) {
    }

    /**
     * @param rows  how many 18px rows the value takes, at least one
     * @param spots each run's place, in order
     */
    record ValueRuns(int rows, List<Spot> spots) {
    }

    /**
     * Lays an item's goo value as runs, one per type, wrapping onto a new row where the next run would pass the
     * width (decision items-rows-read-valued-derived-or-no-value-with-values-inline).
     *
     * @param runWidths each run's width, its type icon and amount
     * @param width     the width the value fills
     * @return the rows it takes and each run's place
     */
    static ValueRuns layValueRuns(List<Integer> runWidths, int width) {
        List<Spot> spots = new ArrayList<>();
        int x = 0;
        int row = 0;
        for (int runWidth : runWidths) {
            if (x > 0 && x + runWidth > width) {
                row++;
                x = 0;
            }
            spots.add(new Spot(x, row));
            x += runWidth + RUN_GAP;
        }
        return new ValueRuns(row + 1, spots);
    }

    private void scrollToRevealed() {
        if (revealRow != NO_REVEAL) {
            listBar.scrollTo(revealRow);
            revealRow = NO_REVEAL;
        }
    }

    /**
     * @param kind where an item's value comes from
     * @return its source's color, red for no value and nothing else
     */
    static int colorOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> GREEN;
            case GROUP -> GOLD;
            case DENIED -> GRAY;
            case DERIVED -> DERIVED_COLOR;
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
