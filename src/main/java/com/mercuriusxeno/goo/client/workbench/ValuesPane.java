package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
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
    static final int LINE_HEIGHT = ExpressionLines.TEXT_LINE_HEIGHT;
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
    // A stack count's right and bottom edges stand a pixel past the sprite's.
    private static final int COUNT_OVERHANG = 1;
    // Where a stack count's glyphs end under the sprite's top: its text top 9 down and its 8px glyphs.
    private static final int COUNT_GLYPHS_BOTTOM = 17;
    /** The room either side of the + between a recipe equivalency's inputs. */
    static final int EQUIVALENCY_GAP = 2;
    /** The room between the search line and the list. */
    static final int GAP = 3;
    private static final int HALF = 2;
    /** The room between two runs of an Items row's value. */
    static final int RUN_GAP = 3;
    /** The room between a run's type icon and its amount. */
    static final int RUN_ICON_GAP = 2;
    // Decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere: an amount stands a pixel further from the next
    // run's icon than the run gap alone.
    private static final int RUN_STEP = RUN_GAP + 1;
    private static final String ITEMS_CHIP = "Items";
    private static final String UNCOVERED_CHIP = "Uncovered ";
    private static final String FRACTIONAL_CHIP = "Fractional";
    private static final String NEGATIVE_CHIP = "Negative ";
    private static final String SEARCH_HINT = "Search items";
    private static final int SEARCH_WIDTH = 180;
    private static final String NO_ENTRIES = "Nothing listed here.";
    private static final String NO_TEXT = "";
    private static final int NO_REVEAL = -1;
    // A hover key no item id, prefab name or goo type key spells.
    private static final String GROUP_MARK = "#";

    private final ValuesTab values;
    private final ItemIcons icons;
    private final ManagerActions actions;
    private @Nullable EditBox search;
    private final ExpressionBuilderPane builder;
    private final Map<String, MeasuredHeight> measured = new HashMap<>();
    // Where every Items row's recipe starts, from the row's left, and what it was measured from.
    private int valueColumn;
    private @Nullable ColumnInputs columnInputs;
    /** How far one wheel notch moves the Items list, in pixels (decision items-list-scrolls-by-pixel). */
    private final ScrollBar listBar = new ScrollBar(ScrollBar.WHEEL_STEP);
    // The item whose expression the builder stands open over, in its row's place.
    private @Nullable String building;
    private boolean visible;
    private int revealRow = NO_REVEAL;
    private final RevealGlow glow = new RevealGlow();

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
        int negative = values.negativeCount();
        List<String> labels = List.of(ITEMS_CHIP, UNCOVERED_CHIP + uncovered, FRACTIONAL_CHIP,
                NEGATIVE_CHIP + negative);
        int left = chipsLeft(labels, font::width, line);
        ChipLayout layout = layChips(labels, font::width, left, line.top(), values.filter());
        for (Chip chip : layout.chips()) {
            drawChip(graphics, font, chip, warns(chip.filter(), uncovered, negative) ? Palette.DANGER : Palette.TEXT,
                    targets);
        }
        // Decision one-search-row-per-list-with-its-create-control-beside-it: the box stretches to the chips.
        SearchRow.Laid laid = SearchRow.lay(line.left(), line.top(), line.right(), line.right() - left);
        SearchRow.place(search, laid);
        return Math.max(layout.bottom(), line.top() + SearchRow.HEIGHT);
    }

    // Uncovered and Negative draw red while they count an item (decision items-filter-set-takes-a-negative-chip).
    private static boolean warns(ValuesTab.Filter filter, int uncovered, int negative) {
        return filter == ValuesTab.Filter.UNCOVERED && uncovered > 0
                || filter == ValuesTab.Filter.NEGATIVE && negative > 0;
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
        // Decision one-frame-primitive-draws-every-bordered-box: a hollow chip frames as a button does.
        Frame.draw(graphics, chip.filled() ? Frame.BUTTON.solid(color) : Frame.BUTTON,
                new GroupsPane.Bounds(chip.left(), chip.top(), right, bottom));
        int labelX = chip.left() + ButtonPainter.PADDING;
        int labelY = Lettering.full(font).centeredTop(chip.top(), chip.height());
        if (chip.filled()) {
            // Decision selected-filter-chip-text-contrasts-its-shadow: the dark label draws with no shadow, which
            // would be dark too.
            graphics.text(font, chip.label(), labelX, labelY, Palette.CHIP_FILLED_TEXT, false);
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
            graphics.text(font, NO_ENTRIES, list.left(), list.top(), Palette.MUTED);
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
        glow.draw(graphics, list.left(), list.top() - listBar.offset(), right);
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
        ValuesTab.EntryRow shown = values.rowOf(entry.id());
        boolean editing = isBuilding(entry);
        // Decisions one-builder-frame-on-every-pane, one-red-error-line-under-every-value: no label and no amount line,
        // the item's goo value standing on its head line.
        int height = BuilderFrame.draw(graphics, font, frameRow(new GroupsPane.Bounds(row.left(), expressionTop,
                row.right(), row.bottom()), entered), new BuilderFrame.Value(editing, expressionRuns(shown),
                shown.error(), box -> editing ? builder.extract(graphics, font, box, box.top(), targets)
                        : drawValueView(graphics, font, shown, box, targets)));
        if (entered) {
            drawRemoval(graphics, font, entry.id(), row.right(), expressionTop, targets);
        }
        // Decision spacing-rules-are-one-set-across-every-pane: the x keeps the rule gap over the rule below.
        return head + height + RowLayout.RULE_SPAN;
    }

    private static BuilderFrame.Row frameRow(GroupsPane.Bounds value, boolean entered) {
        return new BuilderFrame.Row(value, null, GooAmountGrid.NONE, entered);
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
        ButtonPainter.drawColored(graphics, font, DELETE, Palette.DANGER, right - ButtonPainter.width(font, DELETE),
                top, ButtonPainter.HEIGHT, targets, () -> {
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
     * @param valueLeft  the value's left edge, the list's value column, "=" just left of it
     * @param recipeLeft the recipe's left edge, so the recipe ends at the row's right
     * @param recipeTop  the top of the line the recipe draws on
     * @param height     the head's height, one line or two
     */
    record HeadPlaces(int nameLeft, int valueLeft, int recipeLeft, int recipeTop, int height) {
    }

    /**
     * Lays an Items row's head: the name at the left, "=" and the goo value at the list's value column, and the
     * recipe right packed at the far right; where the value would reach the recipe the recipe drops to a second
     * line, still right packed (decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right).
     *
     * @param left        the row's left edge
     * @param top         the row's top edge
     * @param right       the row's right edge
     * @param valueColumn the values' shared left edge, from the row's left
     * @param valueWidth  the value's width, 0 where it draws none
     * @param recipeWidth the recipe's width, 0 where it draws none
     * @return each piece's place
     */
    static HeadPlaces layHead(int left, int top, int right, int valueColumn, int valueWidth, int recipeWidth) {
        int nameLeft = left + ROW_PAD;
        int valueLeft = left + valueColumn;
        int recipeLeft = right - recipeWidth;
        if (recipeWidth == 0 || valueLeft + valueWidth + HEAD_GAP <= recipeLeft) {
            return new HeadPlaces(nameLeft, valueLeft, recipeLeft, top, ROW_HEIGHT);
        }
        return new HeadPlaces(nameLeft, valueLeft, recipeLeft, top + ROW_HEIGHT, ROW_HEIGHT + ROW_HEIGHT);
    }

    // The head's pieces measured and laid for the row's width.
    private HeadPlaces placeHead(Font font, ValuesTab.ManagerEntry entry, GroupsPane.Bounds row) {
        ValuesTab.Source source = values.sourceOf(entry.id());
        return layHead(row.left(), row.top(), row.right(), valueColumn, valueWidthOf(font, entry.id(), source),
                recipeWidth(font, entry.id(), source));
    }

    // Decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right: the values' shared column
    // stands one gap past the widest name the list draws, then the item's sprite, "=" and a gap each, so every value
    // starts at one x.
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
        valueColumn = ROW_PAD + widest + HEAD_GAP + ICON_SIZE + ICON_GAP + font.width(EQUALS) + ICON_GAP;
    }

    private Map<String, String> worthShown(String id, ValuesTab.Source source) {
        return carriesRuns(source.kind()) ? values.worthOf(id) : Map.of();
    }

    private static String headName(Font font, ValuesTab.ManagerEntry entry, int rowWidth) {
        return font.plainSubstrByWidth(entry.name(), rowWidth / NAME_SHARE);
    }

    // Name, = and the goo value at the value column, the recipe right packed, the plain left click on the head
    // selecting the item; answers the head's height.
    // Decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right.
    private int drawHead(GuiGraphicsExtractor graphics, Font font, ValuesTab.ManagerEntry entry,
                         GroupsPane.Bounds row, ClickTargets targets) {
        String name = headName(font, entry, row.right() - row.left());
        HeadPlaces places = placeHead(font, entry, row);
        targets.add(row.left(), row.top(), row.right() - row.left(), places.height(), () -> values.select(entry.id()));
        HeadLine line = headLine(font, row.top(), row.right());
        ValuesTab.Source source = values.sourceOf(entry.id());
        graphics.text(font, name, places.nameLeft(), line.textTop(), Palette.TEXT);
        drawValue(graphics, font, entry.id(), source, places.valueLeft(), line, targets);
        drawRecipe(graphics, font, entry.id(), source, places.recipeLeft(), headLine(font, places.recipeTop(),
                row.right()), targets);
        return places.height();
    }

    private static HeadLine headLine(Font font, int top, int right) {
        return new HeadLine(top + 1, Lettering.full(font).centeredTop(top, ROW_HEIGHT), right);
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

    // A valued, derived or grouped item's goo value, or the no-value or denied words, at the value column.
    private int valueWidthOf(Font font, String id, ValuesTab.Source shown) {
        return carriesRuns(shown.kind()) ? valueWidth(font, worthShown(id, shown)) : font.width(headLabel(shown));
    }

    // What the value equals, drawn at the far right: the group's link, the recipe, or nothing for an entry.
    private int recipeWidth(Font font, String id, ValuesTab.Source shown) {
        return switch (shown.kind()) {
            case GROUP -> ButtonPainter.width(font, GROUP_MARK + shown.label());
            case DERIVED -> inputsWidth(font, id);
            default -> 0;
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
                GooTypeIcons.sizeOf(amount.getKey())) + RUN_STEP).sum();
        return worth.isEmpty() ? 0 : width - RUN_STEP;
    }

    // The item's own sprite and "=" just left of the value column and the goo value after it, or the sprite and the
    // no-value or denied words in the value's place.
    private void drawValue(GuiGraphicsExtractor graphics, Font font, String id, ValuesTab.Source shown, int left,
                           HeadLine line, ClickTargets targets) {
        graphics.item(icons.of(id), left - ICON_GAP - font.width(EQUALS) - ICON_GAP - ICON_SIZE, line.spriteTop());
        if (!carriesRuns(shown.kind())) {
            String label = font.plainSubstrByWidth(headLabel(shown), Math.max(1, line.right() - left));
            graphics.text(font, label, left, line.textTop(), colorOf(shown.kind()));
            return;
        }
        graphics.text(font, EQUALS, left - ICON_GAP - font.width(EQUALS), line.textTop(), Palette.TEXT);
        drawValueRuns(graphics, font, worthShown(id, shown), left, line, targets);
    }

    // The group's link or the recipe at the far right, with no sprite or = before it; an entry draws nothing there.
    private void drawRecipe(GuiGraphicsExtractor graphics, Font font, String id, ValuesTab.Source shown, int left,
                            HeadLine line, ClickTargets targets) {
        if (shown.kind() == ValuesTab.SourceKind.GROUP) {
            drawGroupLink(graphics, font, shown.label(), left, line, targets);
        } else if (shown.kind() == ValuesTab.SourceKind.DERIVED) {
            drawInputs(graphics, font, id, left, line, targets);
        }
    }

    // The group's name as a button a shift + left click follows to its group on the Groups tab, a plain left click
    // passing to the row, hovering its preview (decisions blue-text-options-draw-as-bordered-buttons,
    // group-link-hovers-member-icons-then-plus-n, group-referent-previews-shorthand-members-then-its-value).
    // Decision shift-left-click-follows-plain-left-modifies.
    // Answers the x past the button.
    // Decision hash-marks-groups-and-dollar-marks-prefabs-everywhere: the group reads after #.
    private int drawGroupLink(GuiGraphicsExtractor graphics, Font font, String group, int left, HeadLine line,
                              ClickTargets targets) {
        String shown = font.plainSubstrByWidth(GROUP_MARK + group,
                Math.max(1, line.right() - left - ButtonPainter.PADDING - ButtonPainter.PADDING));
        return ButtonPainter.drawFollow(graphics, font, shown, left, line.spriteTop() - 1, ROW_HEIGHT, targets,
                () -> actions.openGroup(group), GroupHovers.hoverKey(group)).right();
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
            ItemLinks.draw(graphics, icons, targets, actions, input.id(), x, line.spriteTop());
            drawCount(graphics, font, countParts(input.count(), recipe.output()), x, line.spriteTop());
        }
        laid.plusLefts().forEach(x -> graphics.text(font, PLUS, x, line.textTop(), Palette.TEXT));
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
            ItemLinks.draw(graphics, icons, targets, actions, term.id(), x, line.spriteTop());
            drawCount(graphics, font, countParts(term.count(), recipe.output()), x, line.spriteTop());
        }
        laid.plusLefts().forEach(x -> graphics.text(font, MINUS, x, line.textTop(), Palette.TEXT));
        return laid.spriteLefts().getLast() + ICON_SIZE + COUNT_OVERHANG;
    }

    // The count draws small in the sprite's bottom-right corner, its right edge where a stack count's stands and its
    // glyphs a pixel under a stack count's bottom (decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere),
    // its fraction stacking at the value column's size (decision one-lettering-primitive-names-three-text-roles).
    private static void drawCount(GuiGraphicsExtractor graphics, Font font,
                                  StackedFractions.Mixed parts, int x, int y) {
        SmallDigits.drawCorner(graphics, font, parts, x + ICON_SIZE + COUNT_OVERHANG,
                y + COUNT_GLYPHS_BOTTOM - SmallDigits.GLYPH_HEIGHT + 1, Palette.TEXT);
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
            int iconTop = lineTop + (ROW_HEIGHT - size) / HALF;
            GooTypeIcons.draw(graphics, targets, amount.getKey(), x, iconTop, size);
            // Decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere.
            SmallDigits.drawBesideIcon(graphics, font, amount.getValue(), new SmallDigits.Icon(x + size, iconTop, size,
                    RUN_ICON_GAP), Palette.TEXT);
            x += runWidth(font, amount.getValue(), size) + RUN_STEP;
        }
        return worth.isEmpty() ? x : x - RUN_STEP;
    }

    /**
     * @param font     the screen's font
     * @param amount   a goo type's amount as drawn
     * @param iconSize the type icon's side
     * @return the run's width: the type icon, its gap a pixel short and the small amount
     */
    static int runWidth(Font font, String amount, int iconSize) {
        return iconSize + RUN_ICON_GAP - 1 + SmallDigits.width(font, amount);
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
        int height = rowHeight(font, listed.get(target), width);
        listBar.center(targetTop, height);
        // Decision followed-target-glows-briefly.
        glow.start(targetTop, height);
    }

    /**
     * @param kind where an item's value comes from
     * @return its source's color, red for no value and nothing else
     */
    static int colorOf(ValuesTab.SourceKind kind) {
        return switch (kind) {
            case ENTRY -> Palette.GOOD;
            case GROUP -> Palette.GOLD;
            case DENIED -> Palette.MUTED;
            case DERIVED -> Palette.DERIVED;
            case NONE -> Palette.DANGER;
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
        HeightInputs inputs = new HeightInputs(width, valueColumn, row, values.sourceOf(entry.id()),
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
     * @param valueColumn  the values' shared left edge
     * @param row          its name, expression, amounts and error
     * @param source       where its value comes from
     * @param recipe       the recipe its derived value comes from
     * @param entered      whether the file holds an entry of its own
     */
    private record HeightInputs(int width, int valueColumn, ValuesTab.EntryRow row, ValuesTab.Source source,
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
        BuilderFrame.Row frame = frameRow(new GroupsPane.Bounds(0, 0, width, 0), hasNonEmptyEntry(entry));
        BuilderFrame.Laid laid = BuilderFrame.lay(font, frame);
        int expression = ReadValueButton.height(font, expressionRuns(row), laid.boxRight() - laid.valueX());
        int head = placeHead(font, entry, new GroupsPane.Bounds(0, 0, width, 0)).height();
        return head + BuilderFrame.measure(font, frame, expression, row.error()) + RowLayout.RULE_SPAN;
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
                targets, Palette.TEXT, Palette.LINK, ExpressionLines.followedOnShiftClick(targets,
                actions::openVariable)), targets);
    }


    // An empty entry offers neither the x nor the restore arrow; it reads as no entry at all.
    // Decision removed-entry-shows-the-derived-value-and-no-restore-on-empty.
    private boolean hasNonEmptyEntry(ValuesTab.ManagerEntry entry) {
        return values.hasEntry(entry.id()) && !values.rowOf(entry.id()).expression().isBlank();
    }

    // The builder's Cancel writes back the text it opened on; for an item that had no entry that text is blank, and
    // the entry the edit added drops rather than standing blank. Every write goes to the entry the builder opened
    // over, whatever row the list selects meanwhile; each write evaluates the draft afresh, so the value shown is the
    // value the builder holds.
    // Decision cancelling-a-new-entry-leaves-nothing.
    private void openBuilder(ValuesTab.EntryRow row) {
        values.select(row.id());
        building = row.id();
        boolean hadEntry = values.hasEntry(row.id());
        builder.open(row.expression(), typed -> {
            if (!hadEntry && typed.isBlank()) {
                values.dropEntry(row.id());
            } else {
                values.edit(row.id(), typed);
            }
            actions.draftChanged();
        }, () -> {
            building = null;
            actions.draftChanged();
        });
    }
}
