package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Draws the Values tab: the search box with the Items, Uncovered and Fractional set right of it on its line, then
 * each item as one inlined editor spanning the pane, its name, sprite, recipe, goo value and disposition on one line
 * and its value expression on the next, edited in place in the expression builder, the list scrolling under its
 * {@link ScrollBar} (decisions goo-workbench-opens-the-in-game-manager-screen, every-tab-draws-a-scroll-bar,
 * filter-buttons-stand-right-of-the-search-bar, items-row-is-one-line-then-its-expression-inline). The state lives
 * in {@link ValuesTab}.
 */
final class ValuesPane {
    /** A text line's height. */
    static final int LINE_HEIGHT = 10;
    private static final int BOX_HEIGHT = 14;
    /** An Items row's head line's height, its sprite's and margin. */
    static final int ROW_HEIGHT = 18;
    /** The pixel before an Items row's name. */
    static final int ROW_PAD = 1;
    // The name takes at most a third of the row, leaving the line's room to its value.
    private static final int NAME_SHARE = 3;
    // Decision items-row-is-one-line-then-its-expression-inline: a gray pipe parts the head line's pieces.
    private static final String PIPE = "|";
    private static final int PIPE_GAP = 4;
    private static final String EDIT_TO_OVERRIDE = "edit to override";
    private static final String NO_ENTRY = "no entry";
    private static final String DELETE = "✗";
    private static final String DELETE_HINT = "Delete";
    private static final int ICON_SIZE = 16;
    /** The room after an Items row's sprite and after its equals sign. */
    static final int ICON_GAP = 4;
    private static final String EQUALS = "=";
    private static final String PLUS = "+";
    private static final String MINUS = "-";
    private static final String FRACTION_DIVIDER = "/";
    private static final String MIXED_SPACE = " ";
    // Half scale doubles every coordinate the fraction draws at.
    private static final int FRACTION_SPACE = 2;
    // A stack count's right and bottom edges stand a pixel past the sprite's.
    private static final int COUNT_OVERHANG = 1;
    /** The room either side of the + between a recipe equivalency's inputs. */
    static final int EQUIVALENCY_GAP = 2;
    private static final int CHIP_TEXT_NUDGE = 3;
    /** The room between the search line and the list. */
    static final int GAP = 3;
    private static final int HALF = 2;
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
    private static final int GOLD = 0xFFE0C060;
    private static final int CHIP_OFF = 0xFF808080;
    private static final int CHIP_HOLLOW = 0xFF202020;
    private static final int CHIP_FILLED_TEXT = 0xFF000000;
    private static final String ITEMS_CHIP = "Items";
    private static final String UNCOVERED_CHIP = "Uncovered ";
    private static final String FRACTIONAL_CHIP = "Fractional";
    private static final String SEARCH_HINT = "Search items";
    private static final int SEARCH_WIDTH = 180;
    private static final int READ_VIEW_COLOR = 0x30FFFFFF;
    private static final int LINK = ButtonPainter.LINK;
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String NO_TEXT = "";
    private static final int NO_REVEAL = -1;
    // A hover key no item id, prefab name or goo type key spells.
    private static final String GROUP_HOVER_PREFIX = "goo-group-members:";
    private static final int MEMBER_ICONS_SHOWN = 8;
    private static final Disposition VALUED_SYMBOL = new Disposition("✔", GREEN, false);
    private static final Disposition DERIVED_SYMBOL = new Disposition("=", GREEN, false);
    private static final Disposition GROUPED_SYMBOL = new Disposition("#", GREEN, false);
    private static final Disposition NO_VALUE_SYMBOL = new Disposition("?", RED, false);
    // The font's own cancel circle draws half size, so the denied symbol is drawn by pixel.
    private static final Disposition DENIED_SYMBOL = new Disposition("⊘", GREEN, true);
    // A glyph's ink stands this far short of the font's line height.
    private static final int GLYPH_LINE_MARGIN = 2;
    private static final double HALF_PIXEL = 0.5;

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final ScrollBar listBar = new ScrollBar(1);
    // The item whose expression the builder stands open over, in its row's place.
    private @Nullable String building;
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
        search = actions.addWidget(new EditBox(font, 0, 0, SEARCH_WIDTH, BOX_HEIGHT, Component.literal(SEARCH_HINT)));
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

    ScrollBar listBar() {
        return listBar;
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
        building = null;
        revealRow = index;
    }

    /**
     * @param x      the cursor's x
     * @param left   the pane's left edge
     * @param amount the wheel's turn, positive away from the player
     */
    void scroll(double x, int left, double amount) {
        if (!builder.scrollList(amount)) {
            listBar.wheel(amount);
        }
    }

    // Decision items-row-is-one-line-then-its-expression-inline: the list spans the pane, with no detail column.
    void extract(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds bounds, ClickTargets targets) {
        int listTop = drawSearchLine(graphics, font, bounds, targets) + GAP;
        drawEntries(graphics, font, new GroupsPane.Bounds(bounds.left(), listTop, bounds.right(), bounds.bottom()),
                targets);
        builder.drawOverlays(graphics, font, targets);
    }

    /**
     * One box of the Items, Uncovered and Fractional set.
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
     * @param chips  the set's boxes, left to right
     * @param bottom the boxes' bottom
     */
    record ChipLayout(List<Chip> chips, int bottom) {
    }

    /**
     * Lays the filters as one segmented set: adjoining boxes sharing their inner borders, the one the tab holds
     * filled (decision items-filters-sit-on-their-own-line-as-one-set).
     *
     * @param labels   the Items, Uncovered and Fractional labels, in that order (decisions
     *                 items-selector-set-reads-items-and-uncovered, items-filter-set-takes-a-fractional-chip)
     * @param width    a label's width
     * @param left     the set's left edge
     * @param top      the set's top edge
     * @param selected the filter the tab holds
     * @return the boxes and their bottom
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
        return new ChipLayout(chips, top + ButtonPainter.HEIGHT);
    }

    /**
     * @param labels the chips' labels
     * @param width  a label's width
     * @param line   the search line's bounds
     * @return the set's left edge, so its last chip ends at the line's right
     */
    static int chipsLeft(List<String> labels, ToIntFunction<String> width, GroupsPane.Bounds line) {
        int setWidth = labels.stream().mapToInt(label -> width.applyAsInt(label) + ButtonPainter.PADDING
                + ButtonPainter.PADDING - 1).sum() + 1;
        return line.right() - setWidth;
    }

    // The set at the search line's right end, the search box taking the room left of it; answers the line's bottom.
    // Decision filter-buttons-stand-right-of-the-search-bar.
    private int drawSearchLine(GuiGraphicsExtractor graphics, Font font, GroupsPane.Bounds line,
                               ClickTargets targets) {
        int uncovered = values.uncoveredCount();
        List<String> labels = List.of(ITEMS_CHIP, UNCOVERED_CHIP + uncovered, FRACTIONAL_CHIP);
        int left = chipsLeft(labels, font::width, line);
        ChipLayout layout = layChips(labels, font::width, left, line.top(), values.filter());
        for (Chip chip : layout.chips()) {
            drawChip(graphics, font, chip, chip.filter() == ValuesTab.Filter.UNCOVERED && uncovered > 0 ? RED : WHITE,
                    targets);
        }
        if (search != null) {
            search.setPosition(line.left(), line.top());
            search.setWidth(Math.max(1, left - GAP - line.left()));
        }
        return Math.max(layout.bottom(), line.top() + BOX_HEIGHT);
    }

    /**
     * Draws a chip's box and label: the filled box reads its label dark on its color, a hollow one reads its label
     * in the color, dimmed.
     *
     * @param graphics the frame's graphics
     * @param font     the font
     * @param chip     the chip; its filter is not read
     * @param color    its color
     */
    static void drawChipFace(GuiGraphicsExtractor graphics, Font font, Chip chip, int color) {
        int right = chip.left() + chip.width();
        int bottom = chip.top() + chip.height();
        graphics.fill(chip.left(), chip.top(), right, bottom, chip.filled() ? color : CHIP_OFF);
        graphics.fill(chip.left() + 1, chip.top() + 1, right - 1, bottom - 1, chip.filled() ? color : CHIP_HOLLOW);
        int labelX = chip.left() + ButtonPainter.PADDING;
        int labelY = chip.top() + CHIP_TEXT_NUDGE;
        if (chip.filled()) {
            // Decision selected-filter-chip-text-contrasts-its-shadow: the dark label draws with no shadow, which
            // would be dark too.
            graphics.text(font, chip.label(), labelX, labelY, CHIP_FILLED_TEXT, false);
        } else {
            graphics.text(font, chip.label(), labelX, labelY, color);
        }
    }

    // A chip's face, and the click that sets the filter it names.
    private void drawChip(GuiGraphicsExtractor graphics, Font font, Chip chip, int color, ClickTargets targets) {
        drawChipFace(graphics, font, chip, color);
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
        scrollToRevealed(font, listed, list);
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
        int right = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        int y = list.top();
        int whole = 0;
        for (int index = firstShown; index < listed.size() && y < list.bottom(); index++) {
            RowLayout.drawSeparatorAbove(graphics, index - firstShown, y, list.left(), right);
            y += drawEntry(graphics, font, listed.get(index), new GroupsPane.Bounds(list.left(), y, right,
                    list.bottom()), targets);
            whole += y <= list.bottom() ? 1 : 0;
        }
        return whole;
    }

    // One Items row: its head line, then its expression one indent in, its x at that line's right end and the loader
    // error under it; answers its height.
    // Decision items-row-is-one-line-then-its-expression-inline.
    private int drawEntry(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, ClickTargets targets) {
        drawHead(graphics, font, entry, row, targets);
        int expressionTop = row.top() + ROW_HEIGHT;
        boolean entered = values.hasEntry(entry.id());
        int expressionRight = entered ? row.right() - ButtonPainter.width(font, DELETE) - ICON_GAP : row.right();
        GroupsPane.Bounds expression = new GroupsPane.Bounds(row.left() + ControlFlow.INDENT, expressionTop,
                expressionRight, row.bottom());
        ValuesTab.EntryRow shown = values.rowOf(entry.id());
        int height = entry.id().equals(building) && builder.isOpen()
                ? builder.extract(graphics, font, expression, expressionTop, targets)
                : drawValueView(graphics, font, shown, expression, targets);
        if (entered) {
            drawRemoval(graphics, font, entry.id(), row.right(), expressionTop, targets);
        }
        if (!shown.error().isEmpty()) {
            graphics.text(font, font.plainSubstrByWidth(shown.error(), expression.right() - expression.left()),
                    expression.left(), expressionTop + height, RED);
            height += LINE_HEIGHT;
        }
        return ROW_HEIGHT + height + RowLayout.RULE_PADDING;
    }

    // The red x marks the entry for removal; a marked entry's gold arrow in its place restores it until the save.
    // Decision every-delete-marks-for-removal-and-a-gold-arrow-restores.
    private void drawRemoval(GuiGraphicsExtractor graphics, Font font, String id, int right, int top,
                             ClickTargets targets) {
        if (values.isMarked(id)) {
            ButtonPainter.drawRestore(graphics, font, right, top, ButtonPainter.HEIGHT, targets,
                    () -> values.restoreRemoval(id));
            return;
        }
        ButtonPainter.drawColored(graphics, font, DELETE, RED, right - ButtonPainter.width(font, DELETE), top,
                ButtonPainter.HEIGHT, targets, () -> {
                    values.markRemoval(id);
                    actions.markUnsaved();
                }, BuilderOverlays.hintKey(DELETE_HINT));
    }

    /**
     * Where an Items row's head line draws.
     *
     * @param spriteTop the item sprite's top, and every sprite's on the line
     * @param textTop   the line's text top, the glyph line centered on the sprite
     * @param right     the line's right edge
     */
    record HeadLine(int spriteTop, int textTop, int right) {
    }

    // Name | {icon} = {recipe display} | {values list} | {symbol}, a gray pipe between the pieces, the plain left click
    // on the line selecting the item. Decision items-row-is-one-line-then-its-expression-inline.
    private void drawHead(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, ClickTargets targets) {
        targets.add(row.left(), row.top(), row.right() - row.left(), ROW_HEIGHT, () -> values.select(entry.id()));
        HeadLine line = new HeadLine(row.top() + 1, ExpressionLines.centeredTextTop(row.top(), ROW_HEIGHT,
                font.lineHeight), row.right());
        ValuesTab.Source source = values.sourceOf(entry.id());
        String name = font.plainSubstrByWidth(entry.name(), (row.right() - row.left()) / NAME_SHARE);
        graphics.text(font, name, row.left() + ROW_PAD, line.textTop(), WHITE);
        int x = drawPipe(graphics, font, row.left() + ROW_PAD + font.width(name), line);
        x = drawEquality(graphics, font, entry, source, x, line, targets);
        x = drawPipe(graphics, font, x, line);
        x = drawValueRuns(graphics, font, carriesRuns(source.kind()) ? values.worthOf(entry.id()) : Map.of(), x,
                line, targets);
        x = drawPipe(graphics, font, x, line);
        drawSymbol(graphics, font, dispositionOf(source.kind()), x, line.textTop());
    }

    // Answers the x past the pipe and its gaps.
    private static int drawPipe(GuiGraphicsExtractor graphics, Font font, int x, HeadLine line) {
        graphics.text(font, PIPE, x + PIPE_GAP, line.textTop(), GRAY);
        return x + PIPE_GAP + font.width(PIPE) + PIPE_GAP;
    }

    // Valued, derived and grouped items show their goo value; no value and denied have none to show.
    private static boolean carriesRuns(ValuesTab.SourceKind kind) {
        return kind == ValuesTab.SourceKind.ENTRY || kind == ValuesTab.SourceKind.DERIVED
                || kind == ValuesTab.SourceKind.GROUP;
    }

    private static void drawSymbol(GuiGraphicsExtractor graphics, Font font, Disposition symbol, int x, int top) {
        if (symbol.drawn()) {
            int side = symbolSize(symbol, font::width, font.lineHeight).height();
            drawCancelCircle(graphics, x, top, side, symbol.color());
        } else {
            graphics.text(font, symbol.glyph(), x, top, symbol.color());
        }
    }

    // The sprite, then by disposition: "=" and what the value equals, or the no-value or denied words; answers the x
    // past it.
    private int drawEquality(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                             ValuesTab.Source shown, int x, HeadLine line, ClickTargets targets) {
        graphics.item(icons.of(entry.id()), x, line.spriteTop());
        int after = x + ICON_SIZE + ICON_GAP;
        if (!carriesRuns(shown.kind())) {
            String label = font.plainSubstrByWidth(shown.label(), Math.max(1, line.right() - after));
            graphics.text(font, label, after, line.textTop(), colorOf(shown.kind()));
            return after + font.width(label);
        }
        graphics.text(font, EQUALS, after, line.textTop(), WHITE);
        int beside = after + font.width(EQUALS) + ICON_GAP;
        if (shown.kind() == ValuesTab.SourceKind.GROUP) {
            return drawGroupLink(graphics, font, shown.label(), beside, line, targets);
        }
        if (shown.kind() == ValuesTab.SourceKind.DERIVED) {
            return drawInputs(graphics, font, entry.id(), beside, line, targets);
        }
        return beside - ICON_GAP;
    }

    /**
     * @param hoverKey what the hovered area names
     * @return the group's members as one row of item icons where the area is a group button's, empty otherwise
     */
    Optional<List<TooltipLine>> groupTooltipFor(String hoverKey) {
        if (!hoverKey.startsWith(GROUP_HOVER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(List.of(memberIcons(values.groupMembers(hoverKey.substring(GROUP_HOVER_PREFIX.length())))));
    }

    /**
     * @param members a group's members, in member order
     * @return the first eight as icons, then ..+n counting the rest, no label where every member draws (decisions
     *     group-link-hovers-member-icons-then-plus-n, more-button-reads-dot-dot-plus-n)
     */
    static TooltipLine.MemberIcons memberIcons(List<String> members) {
        int shown = Math.min(members.size(), MEMBER_ICONS_SHOWN);
        int hidden = members.size() - shown;
        return new TooltipLine.MemberIcons(members.subList(0, shown), hidden > 0 ? UsedByLine.moreLabel(hidden)
                : NO_TEXT);
    }

    // The group's name as a button a shift + left click follows to its group on the Groups tab, a plain left click
    // passing to the row, hovering its members (decisions blue-text-options-draw-as-bordered-buttons,
    // group-link-hovers-member-icons-then-plus-n).
    // Decision shift-left-click-follows-plain-left-modifies.
    // Answers the x past the button.
    private int drawGroupLink(GuiGraphicsExtractor graphics, Font font, String group, int left, HeadLine line,
                              ClickTargets targets) {
        String shown = font.plainSubstrByWidth(group,
                Math.max(1, line.right() - left - ButtonPainter.PADDING - ButtonPainter.PADDING));
        return ButtonPainter.drawFollow(graphics, font, shown, left, line.spriteTop() - 1, ROW_HEIGHT, targets,
                () -> actions.openGroup(group), GROUP_HOVER_PREFIX + group).right();
    }

    // A derived item's recipe equivalency, or the inverted recipe that solved it; one recipes value with neither to
    // name reads derived. Answers the x past it.
    private int drawInputs(GuiGraphicsExtractor graphics, Font font, String id, int left, HeadLine line,
                           ClickTargets targets) {
        ValuesTab.Recipe recipe = values.derivedFrom(id);
        if (recipe.solvedFrom() != null) {
            return drawInverted(graphics, font, recipe, left, line, targets);
        }
        if (recipe.inputs().isEmpty()) {
            String label = values.sourceOf(id).label();
            graphics.text(font, label, left, line.textTop(), colorOf(ValuesTab.SourceKind.DERIVED));
            return left + font.width(label);
        }
        return drawEquivalency(graphics, font, recipe, left, line, targets);
    }

    // The inputs' sprites, each its summed count over the recipe's output at its bottom right as an inventory draws a
    // stack and a link to its own row, a small + between them; answers the x past the last (decision
    // recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
    private int drawEquivalency(GuiGraphicsExtractor graphics, Font font, ValuesTab.Recipe recipe, int left,
                                HeadLine line, ClickTargets targets) {
        Equivalency laid = layEquivalency(left, recipe.inputs().size(), font.width(PLUS));
        for (int index = 0; index < recipe.inputs().size(); index++) {
            ValuesTab.Input input = recipe.inputs().get(index);
            int x = laid.spriteLefts().get(index);
            ItemStack stack = icons.of(input.id());
            graphics.item(stack, x, line.spriteTop());
            drawCount(graphics, font, stack, countParts(input.count(), recipe.output()), x, line.spriteTop());
            ItemLinks.add(targets, actions, input.id(), x, line.spriteTop());
        }
        laid.plusLefts().forEach(x -> graphics.text(font, PLUS, x, line.textTop(), WHITE));
        return laid.spriteLefts().getLast() + ICON_SIZE + COUNT_OVERHANG;
    }

    // The solving recipe's output, then each other input after a minus, every count over the slots the item fills, so
    // A + B = C reads A = C - B and A + A = C reads A = C / 2; each sprite links to its own row.
    // Decision items-rows-read-valued-derived-or-no-value-with-values-inline.
    private int drawInverted(GuiGraphicsExtractor graphics, Font font, ValuesTab.Recipe recipe, int left,
                             HeadLine line, ClickTargets targets) {
        List<ValuesTab.Input> terms = new ArrayList<>();
        terms.add(recipe.solvedFrom());
        terms.addAll(recipe.less());
        Equivalency laid = layEquivalency(left, terms.size(), font.width(MINUS));
        for (int index = 0; index < terms.size(); index++) {
            ValuesTab.Input term = terms.get(index);
            int x = laid.spriteLefts().get(index);
            ItemStack stack = icons.of(term.id());
            graphics.item(stack, x, line.spriteTop());
            drawCount(graphics, font, stack, countParts(term.count(), recipe.output()), x, line.spriteTop());
            ItemLinks.add(targets, actions, term.id(), x, line.spriteTop());
        }
        laid.plusLefts().forEach(x -> graphics.text(font, MINUS, x, line.textTop(), WHITE));
        return laid.spriteLefts().getLast() + ICON_SIZE + COUNT_OVERHANG;
    }

    // The whole part draws full size as an inventory draws a stack count; the fraction draws at half scale in the
    // sprite's bottom-right corner as its numerator over a bar over its denominator, the whole just left of it.
    private static void drawCount(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                                  StackedFractions.Mixed parts, int x, int y) {
        CornerCount placed = placeCornerCount(x, y, font.width(parts.numerator()), font.width(parts.denominator()));
        if (!parts.whole().isEmpty()) {
            graphics.itemDecorations(font, stack, placed.wholeX(), y, parts.whole());
        }
        if (parts.stacked()) {
            StackedFractions.drawStack(graphics, font, parts, placed.stack(), WHITE);
        }
    }

    /**
     * @param count  an input's summed count
     * @param output how many the recipe makes
     * @return the count over the output as a reduced mixed number, 2 over 6 reading 1/3 and 9 over 4 reading 2 1/4,
     *     empty where it equals 1
     */
    static Optional<String> countLabel(int count, int output) {
        StackedFractions.Mixed parts = countParts(count, output);
        String fraction = parts.stacked() ? parts.numerator() + FRACTION_DIVIDER + parts.denominator() : NO_TEXT;
        String joined = parts.whole().isEmpty() || fraction.isEmpty() ? parts.whole() + fraction
                : parts.whole() + MIXED_SPACE + fraction;
        return joined.isEmpty() ? Optional.empty() : Optional.of(joined);
    }

    /**
     * Where an input count draws on its sprite.
     *
     * @param wholeX the x the whole part's stack decoration takes, so it ends just left of the stack
     * @param stack  the fraction's stack, in half-scale space
     */
    record CornerCount(int wholeX, StackedFractions.Stack stack) {
    }

    /**
     * Stands the half-scale fraction stack in the sprite's bottom-right corner, its right and bottom edges where a
     * stack count's stand, and moves the whole part left by the stack's drawn width (decision
     * recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     *
     * @param spriteLeft       the sprite's left edge
     * @param spriteTop        the sprite's top edge
     * @param numeratorWidth   the numerator's width at full scale, 0 where the count is whole
     * @param denominatorWidth the denominator's width at full scale, 0 where the count is whole
     * @return the whole part's x and the stack
     */
    static CornerCount placeCornerCount(int spriteLeft, int spriteTop, int numeratorWidth, int denominatorWidth) {
        int barWidth = Math.max(numeratorWidth, denominatorWidth);
        int right = (spriteLeft + ICON_SIZE + COUNT_OVERHANG) * FRACTION_SPACE;
        int bottom = (spriteTop + ICON_SIZE + COUNT_OVERHANG) * FRACTION_SPACE;
        int wholeX = barWidth == 0 ? spriteLeft : spriteLeft - barWidth / FRACTION_SPACE - 1;
        return new CornerCount(wholeX, StackedFractions.layStack(right - barWidth,
                bottom - StackedFractions.STACK_HEIGHT, numeratorWidth, denominatorWidth));
    }

    /**
     * @param count  an input's summed count
     * @param output how many the recipe makes
     * @return the count over the output, reduced, split into its whole part and its fraction, all empty where it
     *     equals 1
     */
    static StackedFractions.Mixed countParts(int count, int output) {
        int made = Math.max(1, output);
        return count == made ? StackedFractions.NONE : StackedFractions.of(count, made);
    }

    /**
     * Where a recipe equivalency's parts stand.
     *
     * @param spriteLefts each input sprite's left edge, in order
     * @param plusLefts   each + between inputs' left edge
     */
    record Equivalency(List<Integer> spriteLefts, List<Integer> plusLefts) {
    }

    /**
     * Lays a recipe equivalency's inputs from a left edge: each input sprite, a small + a gap either side between
     * inputs, ending on the last input (decision recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     *
     * @param left      the first sprite's left edge
     * @param inputs    how many distinct inputs the recipe takes
     * @param plusWidth the + sign's width
     * @return each part's place
     */
    static Equivalency layEquivalency(int left, int inputs, int plusWidth) {
        List<Integer> sprites = new ArrayList<>();
        List<Integer> pluses = new ArrayList<>();
        int x = left;
        for (int index = 0; index < inputs; index++) {
            if (index > 0) {
                pluses.add(x + EQUIVALENCY_GAP);
                x += EQUIVALENCY_GAP + plusWidth + EQUIVALENCY_GAP;
            }
            sprites.add(x);
            x += ICON_SIZE;
        }
        return new Equivalency(sprites, pluses);
    }

    // Each type icon at its texture's own size, one texel per pixel, centered on the head line, its amount beside it;
    // answers the x past the last run (decisions items-value-line-draws-type-icons-at-eleven-texels,
    // items-row-is-one-line-then-its-expression-inline).
    private static int drawValueRuns(GuiGraphicsExtractor graphics, Font font, Map<String, String> worth, int left,
                                     HeadLine line, ClickTargets targets) {
        int x = left;
        int lineTop = line.spriteTop() - 1;
        for (Map.Entry<String, String> amount : worth.entrySet()) {
            int size = GooTypeIcons.sizeOf(amount.getKey());
            GooTypeIcons.draw(graphics, targets, amount.getKey(), x, lineTop + (ROW_HEIGHT - size) / HALF, size);
            StackedFractions.draw(graphics, font, amount.getValue(), x + size + RUN_ICON_GAP, line.textTop(), WHITE);
            x += runWidth(font, amount.getValue(), size) + RUN_GAP;
        }
        return worth.isEmpty() ? x : x - RUN_GAP;
    }

    /**
     * @param font     the screen's font
     * @param amount   a goo type's amount as drawn
     * @param iconSize the type icon's side
     * @return the run's width: the type icon, its gap and the amount
     */
    static int runWidth(Font font, String amount, int iconSize) {
        return iconSize + RUN_ICON_GAP + StackedFractions.width(font, StackedFractions.of(amount));
    }

    /**
     * How an item's value disposition reads on its title line.
     *
     * @param glyph the symbol
     * @param color its color
     * @param drawn whether it draws by pixel at the check's size rather than as the font's glyph
     */
    record Disposition(String glyph, int color, boolean drawn) {
    }

    /**
     * @param width  the room a symbol takes on its line
     * @param height its ink's height
     */
    record SymbolSize(int width, int height) {
    }

    /**
     * A glyph symbol takes its font width and the glyph ink's height; the drawn cancel circle takes the check's width
     * and a side that fits both the check's ink and the line (decision
     * denied-symbol-is-a-green-cancel-circle-at-symbol-size).
     *
     * @param symbol     a disposition symbol
     * @param width      a glyph's width in the font
     * @param lineHeight the font's line height
     * @return the room it takes and its ink's height
     */
    static SymbolSize symbolSize(Disposition symbol, ToIntFunction<String> width, int lineHeight) {
        int inkHeight = lineHeight - GLYPH_LINE_MARGIN;
        if (!symbol.drawn()) {
            return new SymbolSize(width.applyAsInt(symbol.glyph()), inkHeight);
        }
        int checkWidth = width.applyAsInt(VALUED_SYMBOL.glyph());
        return new SymbolSize(checkWidth, Math.min(checkWidth - 1, inkHeight));
    }

    // The cancel circle: a ring of the side's diameter and a slash from its top right to its bottom left.
    private static void drawCancelCircle(GuiGraphicsExtractor graphics, int left, int top, int side, int color) {
        double radius = (side - 1) / (double) HALF;
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                double distance = Math.hypot(column - radius, row - radius);
                boolean ring = Math.abs(distance - radius) < HALF_PIXEL;
                boolean slash = column + row == side - 1 && distance <= radius;
                if (ring || slash) {
                    graphics.fill(left + column, top + row, left + column + 1, top + row + 1, color);
                }
            }
        }
    }

    /**
     * Valued a green check, derived a green equals, grouped a green hash, no value a red question mark, denied a green
     * cancel circle at the check's size (decisions items-row-title-carries-a-disposition-symbol-at-the-right-edge,
     * denied-symbol-is-a-green-cancel-circle-at-symbol-size).
     *
     * @param kind where an item's value comes from
     * @return the symbol its title line reads
     */
    static Disposition dispositionOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> VALUED_SYMBOL;
            case DERIVED -> DERIVED_SYMBOL;
            case GROUP -> GROUPED_SYMBOL;
            case NONE -> NO_VALUE_SYMBOL;
            case DENIED -> DENIED_SYMBOL;
        };
    }

    // The list scrolls by whole rows of differing heights, so a revealed row lands on the first shown row that puts
    // its middle nearest the list's middle, no later than the last first row that still fills the list.
    // Decision shift-follow-centers-the-target-in-its-scrolling-view.
    private void scrollToRevealed(Font font, List<ValuesTab.ManagerEntry> listed, GroupsPane.Bounds list) {
        if (revealRow == NO_REVEAL) {
            return;
        }
        int target = Math.min(revealRow, listed.size() - 1);
        revealRow = NO_REVEAL;
        int right = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        IntUnaryOperator height = index -> rowHeight(font, listed.get(index), right - list.left());
        RowFit fit = new RowFit(height, listed.size(), list.bottom() - list.top());
        int first = Math.min(fit.centeredFirst(target), fit.lastFillingFirst());
        listBar.measure(listed.size(), fit.wholeFrom(first));
        listBar.scrollTo(first);
    }

    // Rows of differing heights in a view of a fixed height, by the first row shown.
    private record RowFit(IntUnaryOperator height, int rows, int view) {
        // The first row that puts the target's middle nearest the view's middle, walking up from the target; a target
        // taller than the view is its own first row, shown from its top.
        // Decision tall-follow-target-shows-from-its-top.
        int centeredFirst(int target) {
            double half = view / (double) HALF;
            double middle = height.applyAsInt(target) / (double) HALF;
            int best = target;
            double bestMiss = Math.abs(middle - half);
            for (int first = target - 1; first >= 0 && middle < half; first--) {
                middle += height.applyAsInt(first);
                double miss = Math.abs(middle - half);
                if (miss < bestMiss) {
                    best = first;
                    bestMiss = miss;
                }
            }
            return best;
        }

        // The first row at which the rows to the end still fit the view, the list's end at its bottom.
        int lastFillingFirst() {
            int first = rows - 1;
            int filled = height.applyAsInt(first);
            while (first > 0 && filled + height.applyAsInt(first - 1) <= view) {
                first--;
                filled += height.applyAsInt(first);
            }
            return first;
        }

        // How many rows from the first stand whole in the view, at least one.
        int wholeFrom(int first) {
            int whole = 0;
            int filled = 0;
            for (int index = first; index < rows && filled + height.applyAsInt(index) <= view; index++) {
                filled += height.applyAsInt(index);
                whole++;
            }
            return Math.max(1, whole);
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

    // What a blank expression reads: an item recipes or a group already value overrides that value, any other has
    // no entry. Decision items-row-is-one-line-then-its-expression-inline.
    private List<ExpressionLines.Run> expressionRuns(ValuesTab.EntryRow row) {
        if (!row.expression().isBlank()) {
            return ExpressionLines.shownRuns(row.expression(), icons::nameOf);
        }
        ValuesTab.SourceKind kind = values.sourceOf(row.id()).kind();
        boolean valued = kind == ValuesTab.SourceKind.DERIVED || kind == ValuesTab.SourceKind.GROUP;
        return ExpressionLines.textRuns(valued ? EDIT_TO_OVERRIDE : NO_ENTRY);
    }

    // A row's height as it draws with its expression read: its head line, the expression, the error line and the
    // rule's padding.
    private int rowHeight(Font font, ValuesTab.ManagerEntry entry, int width) {
        ValuesTab.EntryRow row = values.rowOf(entry.id());
        int expression = ExpressionLines.height(font, expressionRuns(row), width - ControlFlow.INDENT);
        return ROW_HEIGHT + expression + (row.error().isEmpty() ? 0 : LINE_HEIGHT) + RowLayout.RULE_PADDING;
    }

    // The entry's value curated, a plain left click on it opening the builder in its place, with the row selected,
    // and a shift + left click on a ref opening its prefab; answers the height it took.
    // Decision expression-builder-composes-every-form-without-typing.
    // Decision shift-left-click-follows-plain-left-modifies.
    // Decision items-row-is-one-line-then-its-expression-inline.
    private int drawValueView(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row,
                              GroupsPane.Bounds area, ClickTargets targets) {
        int x = area.left();
        int y = area.top();
        int width = area.right() - area.left();
        List<ExpressionLines.Run> runs = expressionRuns(row);
        int height = ExpressionLines.height(font, runs, width);
        graphics.fill(x, y, x + width, y + height, READ_VIEW_COLOR);
        targets.add(x, y, width, height, () -> openBuilder(row));
        ExpressionLines.draw(font, runs, x, y, width, new ExpressionLines.Painter(graphics, icons, targets, WHITE,
                LINK, ExpressionLines.followedOnShiftClick(targets, actions::openVariable)));
        return height;
    }

    private void openBuilder(ValuesTab.EntryRow row) {
        values.select(row.id());
        building = row.id();
        builder.open(row.expression(), typed -> {
            values.edit(typed);
            actions.markUnsaved();
        }, () -> {
            building = null;
            actions.draftChanged();
        });
    }
}
