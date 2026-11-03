package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Draws the Values tab: the search box with the Items, Uncovered and Fractional set right of it on its line, then
 * each item as one inlined editor spanning the pane, its name, recipe and right-packed goo value on one line and its
 * value expression on the next, edited in place in the expression builder, the list scrolling under its
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
    /** The least room either side of the recipe on the head line. */
    static final int HEAD_GAP = 4;
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
    private static final int LINK = ButtonPainter.LINK;
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String NO_TEXT = "";
    private static final int NO_REVEAL = -1;
    // A hover key no item id, prefab name or goo type key spells.
    private static final String GROUP_HOVER_PREFIX = "goo-group-members:";
    private static final String GROUP_MARK = "#";
    private static final int MEMBER_ICONS_SHOWN = 8;

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final Map<String, MeasuredHeight> measured = new HashMap<>();
    // Where every Items row's recipe starts, from the row's left, and what it was measured from.
    private int recipeColumn;
    private @Nullable ColumnInputs columnInputs;
    /** How far one wheel notch moves the Items list, in pixels (decision items-list-scrolls-by-pixel). */
    static final int SCROLL_STEP = LINE_HEIGHT * 3;
    private final ScrollBar listBar = new ScrollBar(SCROLL_STEP);
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
        // Each row's height is measured every frame, so the frame reads the draft once rather than once a row.
        values.freeze();
        try {
            int listTop = drawSearchLine(graphics, font, bounds, targets) + GAP;
            drawEntries(graphics, font, new GroupsPane.Bounds(bounds.left(), listTop, bounds.right(),
                    bounds.bottom()), targets);
        } finally {
            values.thaw();
        }
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
        measureRecipeColumn(font, listed, list.right() - ScrollBar.WIDTH - ScrollBar.GAP - list.left());
        scrollToRevealed(font, listed, list);
        graphics.enableScissor(list.left(), list.top(), list.right(), list.bottom());
        targets.clip(list.left(), list.top(), list.right(), list.bottom());
        int contentEnd = drawRows(graphics, font, listed, list, targets);
        targets.unclip();
        graphics.disableScissor();
        listBar.measureAndDraw(graphics, targets, contentEnd, list.right(), list.top(), list.bottom());
    }

    // Draws every row the view reaches, scrolled by the bar's pixel offset, and steps past the rest by their measured
    // heights; the open builder's row always draws, so its height is the one it takes. Answers the y the content ends.
    // Decision items-list-scrolls-by-pixel.
    private int drawRows(GuiGraphicsExtractor graphics, Font font, List<ValuesTab.ManagerEntry> listed,
                         GroupsPane.Bounds list, ClickTargets targets) {
        int right = list.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        int y = list.top() - listBar.offset();
        for (int index = 0; index < listed.size(); index++) {
            ValuesTab.ManagerEntry entry = listed.get(index);
            boolean building = isBuilding(entry);
            int measured = building ? 0 : rowHeight(font, entry, right - list.left());
            if (building || y < list.bottom() && y + measured > list.top()) {
                RowLayout.drawSeparatorAbove(graphics, index, y, list.left(), right);
                y += drawEntry(graphics, font, entry, new GroupsPane.Bounds(list.left(), y, right, list.bottom()),
                        targets);
            } else {
                y += measured;
            }
        }
        return y;
    }

    private boolean isBuilding(ValuesTab.ManagerEntry entry) {
        return entry.id().equals(building) && builder.isOpen();
    }

    // One Items row: its head line, then its expression one indent in, its x at that line's right end and the loader
    // error under it; answers its height.
    // Decision items-row-is-one-line-then-its-expression-inline.
    private int drawEntry(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                          GroupsPane.Bounds row, ClickTargets targets) {
        int head = drawHead(graphics, font, entry, row, targets);
        int expressionTop = row.top() + head;
        boolean entered = hasNonEmptyEntry(entry);
        int expressionLeft = row.left() + ControlFlow.INDENT;
        GroupsPane.Bounds expression = new GroupsPane.Bounds(expressionLeft, expressionTop,
                expressionLeft + expressionWidth(font, entry, row.right() - row.left()), row.bottom());
        ValuesTab.EntryRow shown = values.rowOf(entry.id());
        int height = isBuilding(entry)
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
        // Decision spacing-rules-are-one-set-across-every-pane: the x keeps the rule gap over the rule below.
        return head + height + RowLayout.RULE_SPAN;
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

    /**
     * Where an Items row's head pieces stand.
     *
     * @param nameLeft   the name's left edge
     * @param recipeLeft the recipe's left edge, the sprite's
     * @param recipeTop  the top of the line the recipe draws on
     * @param valueLeft  the value's left edge, so the value ends at the row's right
     * @param height     the head's height, one line or two
     */
    record HeadPlaces(int nameLeft, int recipeLeft, int recipeTop, int valueLeft, int height) {
    }

    /**
     * Lays an Items row's head: the name at the left, the value right packed, and the recipe at the list's recipe
     * column; where the recipe would reach the value the recipe drops to a second line, one indent in under the name
     * (decision items-row-is-name-recipe-space-between-and-value-right-packed).
     *
     * @param left         the row's left edge
     * @param top          the row's top edge
     * @param right        the row's right edge
     * @param recipeColumn the recipes' shared left edge, from the row's left
     * @param recipeWidth  the recipe's width, sprite to last piece
     * @param valueWidth   the value's width, 0 where it draws none
     * @return each piece's place
     */
    static HeadPlaces layHead(int left, int top, int right, int recipeColumn, int recipeWidth, int valueWidth) {
        int nameLeft = left + ROW_PAD;
        int recipeLeft = left + recipeColumn;
        int valueLeft = right - valueWidth;
        if (recipeLeft + recipeWidth + HEAD_GAP <= valueLeft) {
            return new HeadPlaces(nameLeft, recipeLeft, top, valueLeft, ROW_HEIGHT);
        }
        return new HeadPlaces(nameLeft, nameLeft + ControlFlow.INDENT, top + ROW_HEIGHT, valueLeft,
                ROW_HEIGHT + ROW_HEIGHT);
    }

    // The head's pieces measured and laid for the row's width.
    private HeadPlaces placeHead(Font font, ValuesTab.ManagerEntry entry, GroupsPane.Bounds row) {
        ValuesTab.Source source = values.sourceOf(entry.id());
        return layHead(row.left(), row.top(), row.right(), recipeColumn, equalityWidth(font, entry, source),
                valueWidth(font, worthShown(entry.id(), source)));
    }

    // Decision items-row-is-name-recipe-space-between-and-value-right-packed: the recipes' shared column stands one
    // gap past the widest name the list draws, so every recipe starts at one x.
    // Measured again only when the listed rows or the row width change.
    private void measureRecipeColumn(Font font, List<ValuesTab.ManagerEntry> listed, int rowWidth) {
        ColumnInputs inputs = new ColumnInputs(rowWidth, listed);
        if (inputs.equals(columnInputs)) {
            return;
        }
        columnInputs = inputs;
        int widest = 0;
        int cap = rowWidth / NAME_SHARE;
        for (ValuesTab.ManagerEntry entry : listed) {
            int width = font.width(entry.name());
            widest = Math.max(widest, width <= cap ? width : font.width(headName(font, entry, rowWidth)));
        }
        recipeColumn = ROW_PAD + widest + HEAD_GAP;
    }

    private Map<String, String> worthShown(String id, ValuesTab.Source source) {
        return carriesRuns(source.kind()) ? values.worthOf(id) : Map.of();
    }

    private static String headName(Font font, ValuesTab.ManagerEntry entry, int rowWidth) {
        return font.plainSubstrByWidth(entry.name(), rowWidth / NAME_SHARE);
    }

    // Name, the recipe space-between, the value right packed, the plain left click on the head selecting the item;
    // answers the head's height.
    // Decision items-row-is-name-recipe-space-between-and-value-right-packed.
    private int drawHead(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                         GroupsPane.Bounds row, ClickTargets targets) {
        String name = headName(font, entry, row.right() - row.left());
        HeadPlaces places = placeHead(font, entry, row);
        targets.add(row.left(), row.top(), row.right() - row.left(), places.height(), () -> values.select(entry.id()));
        HeadLine line = headLine(font, row.top(), row.right());
        ValuesTab.Source source = values.sourceOf(entry.id());
        graphics.text(font, name, places.nameLeft(), line.textTop(), WHITE);
        drawValueRuns(graphics, font, worthShown(entry.id(), source), places.valueLeft(), line, targets);
        drawEquality(graphics, font, entry, source, places.recipeLeft(), headLine(font, places.recipeTop(),
                row.right()), targets);
        return places.height();
    }

    private static HeadLine headLine(Font font, int top, int right) {
        return new HeadLine(top + 1, ExpressionLines.centeredTextTop(top, ROW_HEIGHT, font.lineHeight), right);
    }

    // Valued, derived and grouped items show their goo value; no value and denied have none to show.
    private static boolean carriesRuns(ValuesTab.SourceKind kind) {
        return kind == ValuesTab.SourceKind.ENTRY || kind == ValuesTab.SourceKind.DERIVED
                || kind == ValuesTab.SourceKind.GROUP;
    }

    // A denied item names its denial beside its sprite; an unvalued one says nothing there, its value line reading
    // Value: Not explicitly set. Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
    private static String headLabel(ValuesTab.Source shown) {
        return shown.kind() == ValuesTab.SourceKind.NONE ? NO_TEXT : shown.label();
    }

    // The width drawEquality takes: the sprite, then the source's words, or "=" and what the value equals.
    private int equalityWidth(Font font, ValuesTab.ManagerEntry entry, ValuesTab.Source shown) {
        int sprite = ICON_SIZE + ICON_GAP;
        if (!carriesRuns(shown.kind())) {
            return sprite + font.width(headLabel(shown));
        }
        int equals = sprite + font.width(EQUALS);
        return switch (shown.kind()) {
            case GROUP -> equals + ICON_GAP + ButtonPainter.width(font, GROUP_MARK + shown.label());
            case DERIVED -> equals + ICON_GAP + inputsWidth(font, entry.id());
            default -> equals;
        };
    }

    private int inputsWidth(Font font, String id) {
        ValuesTab.Recipe recipe = values.derivedFrom(id);
        if (recipe.solvedFrom() != null) {
            return equivalencyWidth(recipe.less().size() + 1, font.width(MINUS));
        }
        if (recipe.inputs().isEmpty()) {
            return font.width(values.sourceOf(id).label());
        }
        return equivalencyWidth(recipe.inputs().size(), font.width(PLUS));
    }

    private static int equivalencyWidth(int terms, int signWidth) {
        return layEquivalency(0, terms, signWidth).spriteLefts().getLast() + ICON_SIZE + COUNT_OVERHANG;
    }

    private static int valueWidth(Font font, Map<String, String> worth) {
        int width = worth.entrySet().stream().mapToInt(amount -> runWidth(font, amount.getValue(),
                GooTypeIcons.sizeOf(amount.getKey())) + RUN_GAP).sum();
        return worth.isEmpty() ? 0 : width - RUN_GAP;
    }

    // The sprite, then by disposition: "=" and what the value equals, or the no-value or denied words; answers the x
    // past it.
    private int drawEquality(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                             ValuesTab.Source shown, int x, HeadLine line, ClickTargets targets) {
        graphics.item(icons.of(entry.id()), x, line.spriteTop());
        int after = x + ICON_SIZE + ICON_GAP;
        if (!carriesRuns(shown.kind())) {
            String label = font.plainSubstrByWidth(headLabel(shown), Math.max(1, line.right() - after));
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
    // Decision hash-marks-groups-and-dollar-marks-prefabs-everywhere: the group reads after #.
    private int drawGroupLink(GuiGraphicsExtractor graphics, Font font, String group, int left, HeadLine line,
                              ClickTargets targets) {
        String shown = font.plainSubstrByWidth(GROUP_MARK + group,
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

    // A revealed row's middle stands at the list's middle, a row taller than the list from its top, the clamp
    // stopping the last rows at the list's end; the rows' read heights place it, the builder closed by the reveal.
    // Decision shift-follow-centers-the-target-in-its-scrolling-view.
    // Decision tall-follow-target-shows-from-its-top.
    private void scrollToRevealed(Font font, List<ValuesTab.ManagerEntry> listed, GroupsPane.Bounds list) {
        if (revealRow == NO_REVEAL) {
            return;
        }
        int target = Math.min(revealRow, listed.size() - 1);
        revealRow = NO_REVEAL;
        int width = list.right() - ScrollBar.WIDTH - ScrollBar.GAP - list.left();
        int targetTop = 0;
        int content = 0;
        for (int index = 0; index < listed.size(); index++) {
            targetTop = index == target ? content : targetTop;
            content += rowHeight(font, listed.get(index), width);
        }
        listBar.measure(content, list.bottom() - list.top());
        listBar.center(targetTop, rowHeight(font, listed.get(target), width));
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

    // A blank expression has no runs, which reads Value: Not explicitly set.
    // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
    private List<ExpressionLines.Run> expressionRuns(ValuesTab.EntryRow row) {
        return row.expression().isBlank() ? List.of() : ExpressionLines.shownRuns(row.expression(), icons::nameOf);
    }

    // A row's height as it draws with its expression read: its head line, the expression, the error line and the
    // rule's padding.
    private int rowHeight(Font font, ValuesTab.ManagerEntry entry, int width) {
        ValuesTab.EntryRow row = values.rowOf(entry.id());
        HeightInputs inputs = new HeightInputs(width, recipeColumn, row, values.sourceOf(entry.id()),
                values.derivedFrom(entry.id()), values.hasEntry(entry.id()));
        MeasuredHeight known = measured.get(entry.id());
        if (known != null && known.inputs().equals(inputs)) {
            return known.height();
        }
        int height = measureRow(font, entry, row, width);
        measured.put(entry.id(), new MeasuredHeight(inputs, height));
        return height;
    }

    /**
     * Everything a row's read height follows from, so a height measured once stands until one of them changes.
     *
     * @param width        the row's width
     * @param recipeColumn the recipes' shared left edge
     * @param row          its name, expression, amounts and error
     * @param source       where its value comes from
     * @param recipe       the recipe its derived value comes from
     * @param entered      whether the file holds an entry of its own
     */
    private record HeightInputs(int width, int recipeColumn, ValuesTab.EntryRow row, ValuesTab.Source source,
                                ValuesTab.Recipe recipe, boolean entered) {
    }

    /**
     * Everything the recipe column follows from.
     *
     * @param rowWidth the rows' width
     * @param listed   the rows the list draws
     */
    private record ColumnInputs(int rowWidth, List<ValuesTab.ManagerEntry> listed) {
    }

    /**
     * @param inputs what the height was measured from
     * @param height the height
     */
    private record MeasuredHeight(HeightInputs inputs, int height) {
    }

    private int measureRow(Font font, ValuesTab.ManagerEntry entry, ValuesTab.EntryRow row, int width) {
        int expression = ReadValueButton.height(font, expressionRuns(row), expressionWidth(font, entry, width));
        int head = placeHead(font, entry, new GroupsPane.Bounds(0, 0, width, 0)).height();
        return head + expression + (row.error().isEmpty() ? 0 : LINE_HEIGHT) + RowLayout.RULE_SPAN;
    }

    // The entry's value curated, a plain left click on it opening the builder in its place, with the row selected,
    // and a shift + left click on a ref opening its prefab; answers the height it took.
    // Decision expression-builder-composes-every-form-without-typing.
    // Decision shift-left-click-follows-plain-left-modifies.
    // Decision items-row-is-one-line-then-its-expression-inline.
    // Decision read-mode-builder-is-a-button-with-a-pencil-segment.
    private int drawValueView(GuiGraphicsExtractor graphics, Font font, ValuesTab.EntryRow row,
                              GroupsPane.Bounds area, ClickTargets targets) {
        return ReadValueButton.draw(graphics, font, expressionRuns(row), new ReadValueButton.Place(area.left(),
                area.top(), area.right(), () -> openBuilder(row)), new ExpressionLines.Painter(graphics, icons,
                targets, WHITE, LINK, ExpressionLines.followedOnShiftClick(targets, actions::openVariable)), targets);
    }

    // The expression's room: one indent in, short of the entry's x where it has one.
    private int expressionWidth(Font font, ValuesTab.ManagerEntry entry, int rowWidth) {
        int width = rowWidth - ControlFlow.INDENT;
        return hasNonEmptyEntry(entry) ? width - ButtonPainter.width(font, DELETE) - ICON_GAP : width;
    }

    // An empty entry offers neither the x nor the restore arrow; it reads as no entry at all.
    // Decision removed-entry-shows-the-derived-value-and-no-restore-on-empty.
    private boolean hasNonEmptyEntry(ValuesTab.ManagerEntry entry) {
        return values.hasEntry(entry.id()) && !values.rowOf(entry.id()).expression().isBlank();
    }

    // The builder's Cancel writes back the text it opened on; for an item that had no entry that text is blank, and
    // the entry the edit added drops rather than standing blank.
    // Decision cancelling-a-new-entry-leaves-nothing.
    private void openBuilder(ValuesTab.EntryRow row) {
        values.select(row.id());
        building = row.id();
        boolean hadEntry = values.hasEntry(row.id());
        builder.open(row.expression(), typed -> {
            if (!hadEntry && typed.isBlank()) {
                values.dropEntry(row.id());
            } else {
                values.edit(typed);
            }
            actions.markUnsaved();
        }, () -> {
            building = null;
            actions.draftChanged();
        });
    }
}
