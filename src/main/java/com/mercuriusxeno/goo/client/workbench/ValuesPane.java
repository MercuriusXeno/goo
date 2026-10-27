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
    /** The room between a run's type icon and its amount. */
    static final int RUN_ICON_GAP = 2;
    private static final int RUN_TEXT_NUDGE = 5;
    // The pixel above and the pixel below a value line's type icon.
    private static final int ICON_HAIR = 1;
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

    // One Items row: the icon and the name over its source across the first 18px row, the item's goo value on value
    // lines under it, each its type icon plus a pixel above and below, wrapped onto as many lines as it needs, then a
    // derived item's "derived from" and its inputs on one 18px line; answers its height (decisions
    // items-rows-read-valued-derived-or-no-value-with-values-inline, items-goo-type-line-always-on-the-second-row,
    // value-line-is-as-tall-as-its-icon).
    private int drawEntry(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, boolean selected, ClickTargets targets) {
        Map<String, String> worth = entry.tag() ? Map.of() : values.worthOf(entry.id());
        List<String> from = entry.tag() ? List.of() : values.derivedFrom(entry.id());
        List<Integer> runWidths = worth.entrySet().stream()
                .map(amount -> runWidth(font, amount.getValue(), GooTypeIcons.sizeOf(amount.getKey()))).toList();
        int iconSize = worth.keySet().stream().mapToInt(GooTypeIcons::sizeOf).max().orElse(0);
        EntryLayout layout = layEntry(row, runWidths, !from.isEmpty(),
                new RowText(labelShown(entry, from), font.lineHeight), iconSize);
        int height = layout.height();
        if (selected) {
            graphics.fill(row.left(), row.top(), row.right(), row.top() + height, SELECTED_ROW_COLOR);
        }
        targets.add(row.left(), row.top(), row.right() - row.left(), height, () -> {
            values.select(entry.id());
            detailBar.reset();
            builder.close();
        });
        drawEntryText(graphics, font, entry, layout, row.top());
        drawValueRuns(graphics, font, worth, layout, targets);
        drawDerivedFrom(graphics, font, from, layout.nameLeft(), layout.derivedTop(), targets);
        return height;
    }

    // A tag's name under its id, or an item's source label, draws under the name; a derived item with inputs names
    // them on its derived-from line instead.
    private boolean labelShown(ValuesTab.ManagerEntry entry, List<String> from) {
        return entry.tag() || values.sourceOf(entry.id()).kind() != ValuesTab.SourceKind.DERIVED || from.isEmpty();
    }

    private void drawEntryText(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                               EntryLayout layout, int top) {
        int textX = layout.nameLeft();
        int width = layout.nameWidth();
        graphics.item(icons.of(entry.id()), textX - ICON_SIZE - ICON_GAP, top + 1);
        graphics.text(font, font.plainSubstrByWidth(entry.tag() ? entry.id() : entry.name(), width), textX,
                layout.nameTop(), WHITE);
        if (entry.tag()) {
            graphics.text(font, font.plainSubstrByWidth(entry.name(), width), textX, top + SECOND_LINE, GRAY);
            return;
        }
        ValuesTab.Source source = values.sourceOf(entry.id());
        if (labelShown(entry, values.derivedFrom(entry.id()))) {
            graphics.text(font, font.plainSubstrByWidth(source.label(), width), textX, top + SECOND_LINE,
                    colorOf(source.kind()));
        }
    }

    // Each type icon at its texture's own size, one texel per pixel, a pixel under its value line's top, its amount
    // beside it (decisions items-value-line-draws-type-icons-at-eleven-texels, value-line-is-as-tall-as-its-icon).
    private static void drawValueRuns(GuiGraphicsExtractor graphics, Font font, Map<String, String> worth,
                                      EntryLayout layout, ClickTargets targets) {
        Iterator<Spot> spots = layout.runs().spots().iterator();
        for (Map.Entry<String, String> amount : worth.entrySet()) {
            Spot spot = spots.next();
            int x = layout.runsLeft() + spot.x();
            int size = GooTypeIcons.sizeOf(amount.getKey());
            GooTypeIcons.draw(graphics, targets, amount.getKey(), x, layout.iconTop(spot.row()), size);
            graphics.text(font, amount.getValue(), x + size + RUN_ICON_GAP, layout.amountTop(spot.row()), WHITE);
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

    /**
     * @param font     the screen's font
     * @param amount   a goo type's amount as drawn
     * @param iconSize the type icon's side
     * @return the run's width: the type icon, its gap and the amount
     */
    static int runWidth(Font font, String amount, int iconSize) {
        return iconSize + RUN_ICON_GAP + font.width(amount);
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

    /**
     * The text an Items row's first row holds.
     *
     * @param labelShown whether a label draws under the name
     * @param lineHeight the font's glyph line height
     */
    record RowText(boolean labelShown, int lineHeight) {
    }

    /**
     * Where an Items row's goo value lines stand.
     *
     * @param left         their left edge
     * @param top          the first line's top
     * @param runs         the runs, none for an empty worth
     * @param lineHeight   a line's height, its type icon plus a pixel above and below
     * @param amountOffset an amount's top under its line's top, the glyph line centered on the icon
     */
    record ValueLines(int left, int top, ValueRuns runs, int lineHeight, int amountOffset) {
    }

    /**
     * Where an Items row's parts stand.
     *
     * @param nameLeft   the name's left edge, past the icon
     * @param nameTop    the name's top
     * @param nameWidth  the width the name and its source are cut to
     * @param value      the goo value's lines
     * @param derivedTop the derived-from line's top
     * @param height     the row's whole height
     */
    record EntryLayout(int nameLeft, int nameTop, int nameWidth, ValueLines value, int derivedTop, int height) {
        int runsLeft() {
            return value.left();
        }

        int runsTop() {
            return value.top();
        }

        ValueRuns runs() {
            return value.runs();
        }

        /**
         * @param line a value line, from 0
         * @return its top
         */
        int lineTop(int line) {
            return value.top() + line * value.lineHeight();
        }

        /**
         * @param line a value line, from 0
         * @return its type icons' top, a pixel under the line's top
         */
        int iconTop(int line) {
            return lineTop(line) + ICON_HAIR;
        }

        /**
         * @param line a value line, from 0
         * @return its amounts' top, the glyph line centered on the icon
         */
        int amountTop(int line) {
            return lineTop(line) + value.amountOffset();
        }
    }

    /**
     * Lays an Items row: the name across the first 18px row to the row's right edge, centered on it when no label
     * sits under it, the goo value under that row at the name's left edge on lines each the type icon plus a pixel
     * above and below, an empty worth taking no line, then the 18px derived-from line where there is one (decisions
     * items-goo-type-line-always-on-the-second-row, value-line-is-as-tall-as-its-icon, item-name-centers-on-its-row).
     *
     * @param row       the row's bounds, its left, top and right edges
     * @param runWidths each goo run's width
     * @param derived   whether the row draws a derived-from line
     * @param text      whether a label sits under the name, and the font's line height
     * @param iconSize  the goo type icons' side
     * @return each part's place and the row's height
     */
    static EntryLayout layEntry(GroupsPane.Bounds row, List<Integer> runWidths, boolean derived, RowText text,
                                int iconSize) {
        int top = row.top();
        int nameLeft = row.left() + ICON_SIZE + ICON_GAP;
        int nameTop = text.labelShown() ? top + 1 : top + (ROW_HEIGHT - text.lineHeight()) / HALF;
        int width = row.right() - nameLeft;
        ValueRuns runs = layValueRuns(runWidths, width);
        int lines = runWidths.isEmpty() ? 0 : runs.rows();
        int lineHeight = iconSize + ICON_HAIR * HALF;
        ValueLines value = new ValueLines(nameLeft, top + ROW_HEIGHT, runs, lineHeight,
                (lineHeight - text.lineHeight()) / HALF);
        int derivedTop = value.top() + lines * lineHeight;
        int height = derivedTop - top + (derived ? ROW_HEIGHT : 0);
        return new EntryLayout(nameLeft, nameTop, width, value, derivedTop, height);
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
        if (selected.isEmpty()) {
            graphics.text(font, NO_SELECTION, x, pane.top(), GRAY);
            return;
        }
        // The heading, the value or its open builder, and the amounts scroll as one page, so a builder taller than
        // the pane reaches its done control under the wheel (decision every-tab-draws-a-scroll-bar).
        GroupsPane.Bounds page = new GroupsPane.Bounds(x, pane.top(), pane.right() - ScrollBar.WIDTH - ScrollBar.GAP,
                pane.bottom());
        graphics.enableScissor(pane.left(), pane.top(), pane.right(), pane.bottom());
        targets.clip(pane.left(), pane.top(), pane.right(), pane.bottom());
        int end = drawPage(graphics, font, selected.get(), page, pane.top() - detailBar.offset(), targets);
        targets.unclip();
        graphics.disableScissor();
        detailBar.measureAndDraw(graphics, targets, end, pane.right(), pane.top(), pane.bottom());
    }

    // Draws the selected entry's page from a scrolled top; answers the y its content ended at.
    private int drawPage(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, GroupsPane.Bounds page,
                         int top, ClickTargets targets) {
        drawHeading(graphics, font, row, new GroupsPane.Bounds(page.left(), top, page.right(), page.bottom()));
        int y = top + LINE_HEIGHT * HALF + GAP;
        graphics.text(font, EXPRESSION_LABEL, page.left(), y, GRAY);
        y += LINE_HEIGHT;
        y += builder.isOpen() ? builder.extract(graphics, font, page, y, targets)
                : drawValueView(graphics, font, row, page.left(), y, page.right() - page.left(), targets);
        return drawAmounts(graphics, font, row, page, y + GAP, targets);
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
                                GroupsPane.Bounds page, int top, ClickTargets targets) {
        int y = top;
        for (Map.Entry<String, String> amount : row.amounts().entrySet()) {
            ExpressionLines.draw(font, ExpressionLines.amountRuns(Map.of(amount.getKey(), amount.getValue())),
                    page.left(), y, page.right() - page.left(), new ExpressionLines.Painter(graphics, icons, targets,
                            WHITE, WHITE, (name, linkX, linkY, linkWidth) -> { }));
            y += ExpressionLines.ICON_LINE_HEIGHT;
        }
        return y;
    }

    // The denial or no-value line, each type's amount and the loader's error; answers the y below them.
    private int drawAmounts(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row, GroupsPane.Bounds page,
                            int top, ClickTargets targets) {
        int y = top;
        if (row.denied()) {
            graphics.text(font, DENIED_LINE, page.left(), y, GRAY);
            y += LINE_HEIGHT;
        } else if (row.amounts().isEmpty()) {
            graphics.text(font, NO_VALUE_LINE, page.left(), y, RED);
            y += LINE_HEIGHT;
        }
        y = drawAmountLines(graphics, font, row, page, y, targets);
        if (!row.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(row.error(), page.right() - page.left()), page.left(), y, RED);
            y += LINE_HEIGHT;
        }
        return y;
    }
}
