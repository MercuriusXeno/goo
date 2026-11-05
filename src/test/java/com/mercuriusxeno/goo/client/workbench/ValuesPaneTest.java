package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Values pane's layouts with no Screen: the filter set on the search box's line, and an Items row's one
 * head line with its expression inline below it (decisions filter-buttons-stand-right-of-the-search-bar,
 * items-row-is-one-line-then-its-expression-inline).
 */
class ValuesPaneTest {
    private static final int LEFT = 10;
    private static final int TOP = 30;
    private static final int RIGHT = 300;
    private static final int LINE_HEIGHT = 9;
    private static final int EQUALS_WIDTH = 6;
    // The goo type icons' side today, and the side they widen to.
    private static final int SMALL_ICON = 11;
    private static final int[] ICON_SIZES = {SMALL_ICON, 12};

    /**
     * The Items, Uncovered and Fractional boxes stand on one line, each sharing its inner border with the next, the
     * one box filled is the filter the tab holds, the pane draws no Tags
     * box, and a click on the last lists the fractional items (decisions items-filters-sit-on-their-own-line-as-one-set,
     * items-selector-set-reads-items-and-uncovered, items-filter-set-takes-a-fractional-chip).
     */
    @Test
    void filtersLayAsOneSetAboveTheSearchBox() {
        ValuesPane.ChipLayout layout = ValuesPane.layChips(List.of("Items", "Uncovered 3", "Fractional"),
                String::length, LEFT, TOP, ValuesTab.Filter.UNCOVERED);
        List<ValuesPane.Chip> chips = layout.chips();
        assertEquals(List.of(ValuesTab.Filter.ITEMS, ValuesTab.Filter.UNCOVERED, ValuesTab.Filter.FRACTIONAL),
                chips.stream().map(ValuesPane.Chip::filter).toList());
        assertEquals(LEFT, chips.getFirst().left());
        for (int index = 1; index < chips.size(); index++) {
            ValuesPane.Chip before = chips.get(index - 1);
            ValuesPane.Chip chip = chips.get(index);
            assertEquals(TOP, chip.top());
            assertEquals(before.height(), chip.height());
            assertEquals(before.left() + before.width() - 1, chip.left());
        }
        assertEquals(List.of(false, true, false), chips.stream().map(ValuesPane.Chip::filled).toList());
        ValuesPane.Chip first = chips.getFirst();
        assertEquals(first.top() + first.height(), layout.bottom());

        Font font = sixPixelFont();
        ValuesTab values = new ValuesTab();
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 200);
        pane.extract(frame.graphics, font, bounds, frame.targets);
        assertEquals(List.of(), frame.texts().stream().filter("Tags"::equals).toList());
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0");
        ValuesPane.Chip fractional = ValuesPane.layChips(labels, font::width,
                ValuesPane.chipsLeft(labels, font::width, bounds), 0, values.filter()).chips().get(2);
        frame.targets.click(fractional.left() + 1, fractional.top() + 1);
        assertEquals(ValuesTab.Filter.FRACTIONAL, values.filter());
    }

    /**
     * A Negative chip stands right of Fractional, sharing its border, carrying the count of items holding an amount
     * below zero, red while that count is above zero and white at zero, and a click on it lists the negative items
     * (decision items-filter-set-takes-a-negative-chip).
     */
    @Test
    void negativeChipCountsTheNegativeItemsRightOfFractional() {
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 1");
        List<ValuesPane.Chip> chips = ValuesPane.layChips(labels, String::length, LEFT, TOP,
                ValuesTab.Filter.ITEMS).chips();
        assertEquals(List.of(ValuesTab.Filter.ITEMS, ValuesTab.Filter.UNCOVERED, ValuesTab.Filter.FRACTIONAL,
                ValuesTab.Filter.NEGATIVE), chips.stream().map(ValuesPane.Chip::filter).toList());
        assertEquals(chips.get(2).left() + chips.get(2).width() - 1, chips.get(3).left());

        Font font = sixPixelFont();
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:beetroot", "Beetroot")), List.of()));
        values.readResolved("""
                {"entries": [{"key": "beetroot", "value": {"jelly": "180", "leaf": "-174"}, "denied": false}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 200);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, bounds, frame.targets);
        assertEquals(Palette.DANGER, frame.find("Negative 1").color());
        List<String> drawnLabels = List.of("Items", "Uncovered 0", "Fractional", "Negative 1");
        ValuesPane.Chip negative = ValuesPane.layChips(drawnLabels, font::width,
                ValuesPane.chipsLeft(drawnLabels, font::width, bounds), 0, values.filter()).chips().get(3);
        frame.targets.click(negative.left() + 1, negative.top() + 1);
        assertEquals(ValuesTab.Filter.NEGATIVE, values.filter());

        DrawnFrame empty = new DrawnFrame();
        ValuesTab none = new ValuesTab();
        new ValuesPane(none, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of))
                .extract(empty.graphics, font, bounds, empty.targets);
        assertEquals(0xFFFFFFFF, empty.find("Negative 0").color());
    }

    /**
     * The Items, Uncovered and Fractional chips stand on the search box's line right of it, each a click away from
     * its filter, and the first row starts under that line (decision filter-buttons-stand-right-of-the-search-bar).
     */
    @Test
    void filtersStandRightOfTheSearchBoxOnItsLine() {
        Font font = DrawnFrame.font();
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        List<net.minecraft.client.gui.components.AbstractWidget> widgets = new java.util.ArrayList<>();
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        pane.init(font);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 200);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, bounds, frame.targets);
        net.minecraft.client.gui.components.EditBox search = widgets.stream()
                .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast).findFirst().orElseThrow();
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0");
        List<ValuesPane.Chip> chips = ValuesPane.layChips(labels, font::width,
                ValuesPane.chipsLeft(labels, font::width, bounds), bounds.top(), values.filter()).chips();
        chips.forEach(chip -> assertEquals(search.getY(), chip.top(), chip.toString()));
        assertTrue(chips.getFirst().left() > search.getX() + search.getWidth(), chips.getFirst() + " vs search right "
                + (search.getX() + search.getWidth()));
        int chipsBottom = chips.getFirst().top() + chips.getFirst().height();
        assertTrue(frame.find("Stick").y() > chipsBottom, frame.find("Stick") + " vs " + chipsBottom);
        for (ValuesPane.Chip chip : chips.reversed()) {
            assertTrue(frame.targets.click(chip.left() + 1, chip.top() + 1), chip.toString());
            assertEquals(chip.filter(), values.filter());
        }
    }

    /**
     * Items is one inlined editor: a row reads its name, = and its value, then its right-packed recipe on one line, its
     * expression one indent in on the next with no label, a click there opening the builder in its place, an entry row's x at the
     * expression line's right end, and the loader error under it, the rows spanning the pane with no detail column
     * (decision items-row-is-one-line-then-its-expression-inline).
     */
    @org.junit.jupiter.api.Nested
    class InlineRows {
        private static final GroupsPane.Bounds PANE = new GroupsPane.Bounds(0, 0, 500, 300);
        private static final int LIST_RIGHT = PANE.right() - ScrollBar.WIDTH - ScrollBar.GAP;
        private final ValuesTab values = new ValuesTab();
        private final ManagerActions actions = mock(ManagerActions.class);
        private final ExpressionBuilderPane builder;
        private final ValuesPane pane;

        InlineRows() {
            values.readEntries(new ListedEntrySource(List.of(
                    new ManagerEntrySource.ItemName("minecraft:stick", "Stick"),
                    new ManagerEntrySource.ItemName("minecraft:oak_planks", "Oak Planks"),
                    new ManagerEntrySource.ItemName("minecraft:gravel", "Gravel"),
                    new ManagerEntrySource.ItemName("minecraft:cobblestone", "Cobblestone")), List.of()));
            values.readModel("""
                    {"path": "base_values.json", "members": [
                      {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood\\"", "entries": [],
                       "leading": null, "trailing": null, "closingGap": null},
                      {"key": "cobblestone", "keySeparator": null, "kind": "VALUE", "text": "\\"$missing\\"",
                       "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                    """);
            values.readResolved("""
                    {"entries": [{"key": "stick", "value": {"metal": "4"}, "denied": false},
                      {"key": "oak_planks", "value": {"glow": "2"}, "denied": false},
                      {"key": "cobblestone", "value": null, "denied": false, "error": "Unknown constant $missing"}]}
                    """);
            values.readDerivedFrom("""
                    {"items": {"minecraft:oak_planks": {"inputs": [{"id": "minecraft:oak_log", "count": 1}],
                      "output": 4}}}
                    """);
            ItemIcons icons = mock(ItemIcons.class);
            builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
            pane = new ValuesPane(values, icons, actions, builder);
        }

        // The log input's 1/4 count draws a half-scale stack under the frame's pose.
        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), PANE, frame.targets);
            return frame;
        }

        // A row's texts, the small amounts a few pixels under its words (decision
        // goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
        private static List<DrawnFrame.Text> onLine(DrawnFrame frame, DrawnFrame.Text anchor) {
            return frame.drawn().stream().filter(text -> Math.abs(text.y() - anchor.y()) <= AMOUNT_DROP)
                    .sorted(java.util.Comparator.comparingInt(DrawnFrame.Text::x)).toList();
        }

        private static List<Integer> itemXsAround(DrawnFrame frame, int y) {
            ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).item(any(), xs.capture(), ys.capture());
            List<Integer> found = new java.util.ArrayList<>();
            for (int index = 0; index < xs.getAllValues().size(); index++) {
                if (Math.abs(ys.getAllValues().get(index) - y) < ROW_HEIGHT_OF_LINE) {
                    found.add(xs.getAllValues().get(index));
                }
            }
            return found;
        }

        private static final int ROW_HEIGHT_OF_LINE = 9;
        private static final int AMOUNT_DROP = 3;

        // The shared column every value starts at: one gap past the widest name, then the item's sprite, "=" and a
        // gap each.
        private int valueColumn() {
            int widest = List.of("Stick", "Oak Planks", "Gravel", "Cobblestone").stream()
                    .mapToInt(DrawnFrame.font()::width).max().orElseThrow();
            return PANE.left() + ValuesPane.ROW_PAD + widest + ValuesPane.HEAD_GAP + RowLayout.SPRITE_SIZE
                    + ValuesPane.ICON_GAP + DrawnFrame.font().width("=") + ValuesPane.ICON_GAP;
        }

        // The item's own sprite stands between its name and its "=".
        private static void assertOwnSpriteBetweenNameAndEquals(DrawnFrame.Text name, DrawnFrame.Text equals,
                                                                int sprite) {
            assertTrue(sprite >= name.x() + DrawnFrame.font().width(name.text())
                    && sprite + RowLayout.SPRITE_SIZE <= equals.x(), "the sprite at " + sprite + " between " + name
                    + " and " + equals);
        }

        // Each goo icon's x drawn on the line at a y.
        private static List<Integer> gooIconXsAround(DrawnFrame frame, int y) {
            ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeast(0)).blit(any(), any(), xs.capture(), ys.capture(),
                    org.mockito.ArgumentMatchers.anyFloat(), org.mockito.ArgumentMatchers.anyFloat(), anyInt(),
                    anyInt(), anyInt(), anyInt(), anyInt());
            List<Integer> found = new java.util.ArrayList<>();
            for (int index = 0; index < xs.getAllValues().size(); index++) {
                if (Math.abs(ys.getAllValues().get(index) - y) < ROW_HEIGHT_OF_LINE) {
                    found.add(xs.getAllValues().get(index));
                }
            }
            return found;
        }

        /**
         * A derived row reads its name, its own sprite, =, its goo value at the shared value column, and its recipe
         * right packed at the row's right with no sprite or = before it; an explicit entry draws its sprite and value
         * and no recipe piece (decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right).
         */
        @Test
        void rowReadsItemEqualsGooValuesThenTheRecipeAtTheFarRight() {
            DrawnFrame frame = frame();
            DrawnFrame.Text planks = frame.find("Oak Planks");
            List<DrawnFrame.Text> line = onLine(frame, planks);
            assertEquals(List.of("Oak Planks", "=", "2"), line.stream().map(DrawnFrame.Text::text).toList(),
                    frame.drawn().toString());
            DrawnFrame.Text equals = line.get(1);
            assertTrue(equals.x() > planks.x() + DrawnFrame.font().width(planks.text())
                    && equals.x() + DrawnFrame.font().width("=") < valueColumn(), "= between the name and the value");
            assertEquals(List.of(valueColumn()), gooIconXsAround(frame, planks.y()), "the value at the column");
            List<Integer> sprites = itemXsAround(frame, planks.y());
            assertEquals(2, sprites.size(), "the planks sprite and the log input: " + sprites);
            assertOwnSpriteBetweenNameAndEquals(planks, equals, sprites.getFirst());
            assertEquals(LIST_RIGHT, sprites.getLast() + RowLayout.SPRITE_SIZE + 1, "the recipe at the far right");
            DrawnFrame.Text stick = frame.find("Stick");
            List<Integer> stickSprites = itemXsAround(frame, stick.y());
            assertEquals(1, stickSprites.size(), "an explicit entry's own sprite alone, no recipe piece");
            assertOwnSpriteBetweenNameAndEquals(stick, onLine(frame, stick).get(1), stickSprites.getFirst());
            assertEquals(List.of(valueColumn()), gooIconXsAround(frame, stick.y()), "its value at the column");
            assertTrue(frame.texts().stream().noneMatch(text -> text.equals("|") || text.equals("✔")
                    || text.equals("?")), frame.texts().toString());
        }

        // Decision spacing-rules-are-one-set-across-every-pane: an entry row's x keeps the rule gap over the rule
        // below its row.
        @Test
        void itemsRemoveButtonKeepsTheRuleGapOverTheRuleBelow() {
            // A Torch lists after Stick by name, so a rule stands under the stick's row.
            values.readEntries(new ListedEntrySource(List.of(
                    new ManagerEntrySource.ItemName("minecraft:stick", "Stick"),
                    new ManagerEntrySource.ItemName("minecraft:torch", "Torch")), List.of()));
            DrawnFrame frame = frame();
            DrawnFrame.Text stick = frame.find("Stick");
            DrawnFrame.Text remove = frame.drawn().stream().filter(text -> text.text().equals("✗")
                    && text.y() > stick.y()).findFirst().orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            int removeBottom = remove.y() - ButtonPainter.TEXT_NUDGE + ButtonPainter.HEIGHT;
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(),
                    eq(Palette.SEPARATOR));
            int rule = tops.getAllValues().stream().filter(top -> top >= removeBottom).min(Integer::compare)
                    .orElseThrow(() -> new AssertionError("no rule under " + removeBottom + ": " + tops.getAllValues()));
            assertEquals(RowLayout.RULE_GAP, rule - removeBottom, "the x over the rule at " + rule);
        }

        /**
         * Where the value would meet the recipe, the recipe drops to a second line, still right packed, the value
         * keeping its place after the name (decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-
         * far-right).
         */
        @Test
        void narrowRowDropsItsRecipeToASecondLineAtTheFarRight() {
            GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 110, 300);
            int listRight = narrow.right() - ScrollBar.WIDTH - ScrollBar.GAP;
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), narrow, frame.targets);
            DrawnFrame.Text planks = frame.find("Oak Planks");
            assertEquals(List.of("Oak Planks", "=", "2"), onLine(frame, planks).stream().map(DrawnFrame.Text::text)
                    .toList(), frame.drawn().toString());
            assertEquals(1, itemXsAround(frame, planks.y()).size(), "the recipe left the name line, its own sprite "
                    + "stays: " + itemXsAround(frame, planks.y()));
            List<Integer> dropped = itemXsAround(frame, planks.y() + ValuesPane.ROW_HEIGHT);
            assertEquals(1, dropped.size(), "the recipe on the second line: " + dropped);
            assertEquals(listRight, dropped.getFirst() + RowLayout.SPRITE_SIZE + 1, "still at the far right");
        }

        // Decision no-value-reads-value-not-explicitly-set-as-a-pencil-button.
        @Test
        void unvaluedRowsReadNotExplicitlySetAndOpenTheBuilderInPlace() {
            DrawnFrame frame = frame();
            List<DrawnFrame.Text> unset = frame.drawn().stream()
                    .filter(text -> text.text().equals(ReadValueButton.NOT_SET)).toList();
            assertEquals(2, unset.size(), "the planks recipes value and the gravel nothing values: " + frame.texts());
            assertTrue(frame.texts().stream().noneMatch(List.of("edit to override", "no value", "no entry", "empty")
                    ::contains), frame.texts().toString());
            DrawnFrame.Text gravel = unset.getLast();
            assertEquals(ControlFlow.INDENT + ButtonPainter.PADDING, gravel.x());
            assertTrue(gravel.y() > frame.find("Gravel").y(), frame.drawn().toString());
            DrawnFrame.Text pencil = pencilBeside(frame, gravel);
            assertTrue(pencil.x() > gravel.x() + DrawnFrame.font().width(gravel.text()), pencil + " vs " + gravel);
            frame.click(ReadValueButton.NOT_SET);
            assertTrue(builder.isOpen());
            DrawnFrame open = frame();
            DrawnFrame.Text kind = open.find(ControlFlow.dropdownLabel("by type"));
            assertEquals(gravel.x() - ButtonPainter.PADDING, open.buttonAround(kind)[0], kind + " vs " + gravel);
        }

        // Decision read-mode-builder-is-a-button-with-a-pencil-segment.
        @Test
        void valuedRowFramesItsExpressionAndItsPencilOpensTheBuilder() {
            DrawnFrame frame = frame();
            DrawnFrame.Text wood = frame.find("$wood");
            DrawnFrame.Text pencil = pencilBeside(frame, wood);
            assertTrue(pencil.x() > wood.x() + DrawnFrame.font().width(wood.text()), pencil + " vs " + wood);
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> bottoms = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), tops.capture(), rights.capture(),
                    bottoms.capture(), eq(Palette.BORDER));
            boolean expressionFramed = IntStream.range(0, lefts.getAllValues().size()).anyMatch(index ->
                    lefts.getAllValues().get(index) < wood.x()
                            && rights.getAllValues().get(index) > wood.x() + DrawnFrame.font().width(wood.text())
                            && rights.getAllValues().get(index) <= pencil.x()
                            && tops.getAllValues().get(index) <= wood.y()
                            && bottoms.getAllValues().get(index) >= wood.y() + LINE_HEIGHT);
            assertTrue(expressionFramed, "no frame spans the expression short of its pencil");
            assertTrue(frame.targets.click(pencil.x() + 1, pencil.y() + 1));
            assertTrue(builder.isOpen());
            assertTrue(frame().texts().stream().anyMatch(text -> text.equals(ControlFlow.dropdownLabel("by type"))
                    || text.endsWith(ControlFlow.dropdownLabel(""))), "the builder's kind dropdown draws");
        }

        private DrawnFrame.Text pencilBeside(DrawnFrame frame, DrawnFrame.Text text) {
            return frame.drawn().stream().filter(drawn -> drawn.text().equals(ReadValueButton.PENCIL)
                    && Math.abs(drawn.y() - text.y()) < ExpressionLines.TEXT_LINE_HEIGHT).findFirst()
                    .orElseThrow(() -> new AssertionError("no pencil beside " + text + " in " + frame.drawn()));
        }

        @Test
        void entryRowsXMarksTheEntryAndTheErrorDrawsUnderTheExpression() {
            DrawnFrame frame = frame();
            DrawnFrame.Text stick = frame.find("Stick");
            DrawnFrame.Text wood = frame.find("$wood");
            DrawnFrame.Text cross = frame.drawn().stream().filter(text -> text.text().equals("✗")
                    && Math.abs(text.y() - wood.y()) < ExpressionLines.TEXT_LINE_HEIGHT).findFirst()
                    .orElseThrow(() -> new AssertionError(frame.drawn().toString()));
            assertTrue(cross.y() > stick.y() && Math.abs(cross.y() - wood.y()) < ExpressionLines.TEXT_LINE_HEIGHT,
                    cross + " vs " + wood);
            assertEquals(LIST_RIGHT - 6 - ButtonPainter.PADDING, cross.x());
            assertTrue(frame.targets.click(cross.x() + 1, cross.y() + 1));
            assertTrue(values.isMarked("minecraft:stick"));
            DrawnFrame marked = frame();
            DrawnFrame.Text arrow = marked.find("↺");
            assertEquals(List.of(cross.x(), cross.y()), List.of(arrow.x(), arrow.y()));
            DrawnFrame.Text error = marked.find("Unknown constant $missing");
            assertEquals(RED, error.color());
            assertTrue(error.y() > marked.find("$missing").y(), error + " under the expression");
        }
    }

    private static final int RED = 0xFFFF5555;

    /**
     * The selected chip draws its dark label with no drop shadow, so the label contrasts both its light fill and
     * any shadow under it (decision selected-filter-chip-text-contrasts-its-shadow).
     */
    @Test
    void selectedChipLabelDrawsDarkWithNoShadow() {
        ValuesTab values = new ValuesTab();
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, sixPixelFont(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0");
        ValuesPane.Chip selected = ValuesPane.layChips(labels, sixPixelFont()::width,
                ValuesPane.chipsLeft(labels, sixPixelFont()::width, new GroupsPane.Bounds(0, 0, 500, 200)), 0,
                values.filter()).chips().getFirst();
        ArgumentCaptor<Integer> colors = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics).text(any(Font.class), eq("Items"), anyInt(), anyInt(), colors.capture(), eq(false));
        verify(frame.graphics).fill(selected.left() + 1, selected.top() + 1, selected.left() + selected.width() - 1,
                selected.top() + selected.height() - 1, 0xFFFFFFFF);
        assertNotEquals(0xFFFFFFFF, (int) colors.getValue(), "the label contrasts its white fill");
        assertEquals(List.of(), frame.placements().stream().filter(placed -> placed.text().equals("Items")
                && placed.shadow()).toList(), "no shadowed Items label draws under it");
    }

    private static Font sixPixelFont() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * In the selected item's value view, a right click on a prefab ref runs nothing, a shift + left click on it opens
     * the Prefabs tab on that prefab and a plain left click on the same ref opens the builder over the value
     * (decision shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void valueViewRefFollowsOnShiftClickAndOpensTheBuilderOnPlainClick() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.select("minecraft:stick");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        ValuesPane pane = new ValuesPane(values, icons, actions, builder);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertTrue(!frame.rightClick("$wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(!builder.isOpen());
        frame.shiftClick("$wood");
        verify(actions).openVariable("wood");
        assertTrue(!builder.isOpen());
        frame.click("$wood");
        assertTrue(builder.isOpen());
    }

    /**
     * Each change the builder writes evaluates the draft afresh while the builder stays open, so the value the row
     * shows is the value the builder holds.
     */
    @Test
    void eachBuilderChangeEvaluatesTheDraftAfresh() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.select("minecraft:stick");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = mock(ExpressionBuilderPane.class);
        ValuesPane pane = new ValuesPane(values, icons, actions, builder);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        frame.click("$wood");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.function.Consumer<String>> change =
                ArgumentCaptor.forClass(java.util.function.Consumer.class);
        verify(builder).open(anyString(), change.capture(), any());
        change.getValue().accept("\"$wood / 8\"");
        verify(actions).draftChanged();
    }

    /**
     * The builder open in an Items row over the operator's beetroot value, driven through the pane's click targets as
     * the screen drives them: the press, each drag and the release.
     */
    @org.junit.jupiter.api.Nested
    class BuilderDragsInARow {
        private static final String BEETROOT = "minecraft:beetroot";
        private static final String OPENED = "{\\\"jelly\\\": \\\"$food.jelly + $saturation.jelly * 12\\\", "
                + "\\\"leaf\\\": \\\"$dye.leaf - ($food.jelly + $saturation.jelly * 12)\\\"}";
        private final ValuesTab values = new ValuesTab();
        private final ValuesPane pane;

        BuilderDragsInARow() {
            values.readEntries(new ListedEntrySource(
                    List.of(new ManagerEntrySource.ItemName(BEETROOT, "Beetroot")), List.of()));
            load(OPENED);
            values.select(BEETROOT);
            ManagerActions actions = mock(ManagerActions.class);
            ItemIcons icons = mock(ItemIcons.class);
            pane = new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of,
                    List::of));
        }

        // The beetroot's entry, its text escaped for the model's JSON.
        private void load(String text) {
            values.readModel("""
                    {"path": "base_values.json", "members": [
                      {"key": "beetroot", "keySeparator": null, "kind": "VALUE", "text": "%s", "entries": [],
                       "leading": null, "trailing": null, "closingGap": null}]}
                    """.formatted(text));
        }

        // Decision diagnose-and-correct-parenthetical-trash-and-out-drags: the last term out takes the
        // parenthetical with it.
        @Test
        void lastTermDraggedOutOfAParentheticalTakesItAlong() {
            load("\\\"$waxed + ($edited)\\\"");
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
            frame.click("$waxed");
            DrawnFrame builder = frame();
            double[] rail = railOf(builder, List.of(1, 0));
            assertTrue(builder.targets.click(rail[0], rail[1]));
            DrawnFrame.Text close = builder.drawn().stream().filter(text -> text.text().equals(")")).findFirst()
                    .orElseThrow();
            assertTrue(builder.targets.drag(close.x() + 20, close.y() + 1));
            frame();
            assertTrue(builder.targets.release());
            assertEquals("$waxed + $edited", written());
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
            return frame;
        }

        // Opens the builder by a plain click on the read view's first ref, and answers the builder's first frame.
        private DrawnFrame opened() {
            frame().click("$food");
            return frame();
        }

        private static double[] railOf(DrawnFrame frame, List<Integer> path) {
            String key = ExpressionBuilderPane.railName(path);
            for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
                for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
                    if (frame.targets.grabNamed(x, y).filter(key::equals).isPresent()) {
                        return new double[] {x, y};
                    }
                }
            }
            throw new AssertionError("no rail for " + path);
        }

        // The trash strip's middle on a y, read off its rest-tone fill.
        private static double stripX(DrawnFrame frame) {
            ArgumentCaptor<Integer> lefts = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rights = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).fill(lefts.capture(), anyInt(), rights.capture(), anyInt(),
                    eq(Palette.TRASH_REST));
            return (lefts.getValue() + rights.getValue()) / 2.0;
        }

        private String written() {
            return values.rowOf(BEETROOT).expression();
        }

        // Decision diagnose-and-correct-parenthetical-trash-and-out-drags.
        @Test
        void parentheticalReleasedOnTheTrashStripGoes() {
            DrawnFrame frame = opened();
            double[] rail = railOf(frame, List.of(1, 1));
            assertTrue(frame.targets.click(rail[0], rail[1]));
            assertTrue(frame.targets.drag(stripX(frame), rail[1]));
            frame();
            assertTrue(frame.targets.release());
            assertEquals("{\"jelly\": \"$food.jelly + $saturation.jelly * 12\", \"leaf\": \"$dye.leaf\"}", written());
        }

        // Decision diagnose-and-correct-parenthetical-trash-and-out-drags.
        @Test
        void termDraggedOutOfTheParentheticalLandsAfterIt() {
            DrawnFrame frame = opened();
            double[] rail = railOf(frame, List.of(1, 1, 0));
            assertTrue(frame.targets.click(rail[0], rail[1]));
            double[] after = railOf(frame, List.of(1, 1));
            DrawnFrame.Text close = frame.drawn().stream().filter(text -> text.text().equals(")")).findFirst()
                    .orElseThrow();
            assertTrue(frame.targets.drag(close.x() + 20, close.y() + 1));
            frame();
            assertTrue(frame.targets.release());
            assertEquals("{\"jelly\": \"$food.jelly + $saturation.jelly * 12\", "
                    + "\"leaf\": \"$dye.leaf - ($saturation.jelly * 12) + $food.jelly\"}", written(),
                    "dragged from the parenthetical at " + after[0]);
        }
    }

    /**
     * The builder writes to the entry it opened over, whatever row the list selects while it stands open: the
     * operator, editing beetroot soup, saw the soup's value land in beetroot's entry.
     */
    @org.junit.jupiter.api.Nested
    class BuilderWritesItsOwnEntry {
        private static final String BEETROOT = "minecraft:beetroot";
        private static final String SOUP = "minecraft:beetroot_soup";

        @Test
        void editAfterSelectingAnotherRowLandsOnTheOpenedEntry() {
            ValuesTab values = new ValuesTab();
            values.readEntries(new ListedEntrySource(List.of(new ManagerEntrySource.ItemName(BEETROOT, "Beetroot"),
                    new ManagerEntrySource.ItemName(SOUP, "Beetroot Soup")), List.of()));
            values.readModel("""
                    {"path": "base_values.json", "members": [
                      {"key": "beetroot", "keySeparator": null, "kind": "VALUE", "text": "4", "entries": [],
                       "leading": null, "trailing": null, "closingGap": null},
                      {"key": "beetroot_soup", "keySeparator": null, "kind": "VALUE", "text": "6", "entries": [],
                       "leading": null, "trailing": null, "closingGap": null}]}
                    """);
            ManagerActions actions = mock(ManagerActions.class);
            ItemIcons icons = mock(ItemIcons.class);
            ValuesPane pane = new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons,
                    List::of, List::of));
            DrawnFrame read = new DrawnFrame();
            pane.extract(read.graphics, DrawnFrame.font(), SHORT_VIEW, read.targets);
            read.click("6");
            DrawnFrame building = new DrawnFrame();
            pane.extract(building.graphics, DrawnFrame.font(), SHORT_VIEW, building.targets);
            building.click("Beetroot");
            DrawnFrame selected = new DrawnFrame();
            pane.extract(selected.graphics, DrawnFrame.font(), SHORT_VIEW, selected.targets);
            assertTrue(selected.texts().contains("✗ Cancel"), "the builder stands open: " + selected.texts());
            selected.clickFirst("+");
            assertEquals(List.of("4", "-6"), List.of(values.rowOf(BEETROOT).expression(),
                    values.rowOf(SOUP).expression()), "beetroot's and the soup's values after the soup's edit");
        }
    }

    /**
     * An Items row's loader error wraps onto as many lines as it takes inside the row, so the whole error reads: the
     * operator saw beetroot's error run off the screen.
     */
    @Test
    void longRowErrorWrapsWhole() {
        String error = "beetroot: unrecognized token 'beetroot'; beetroot: unrecognized token 'bowl'";
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(List.of(new ManagerEntrySource.ItemName("minecraft:beetroot",
                "Beetroot")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "beetroot", "keySeparator": null, "kind": "VALUE", "text": "\\"beetroot + bowl\\"",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "beetroot", "value": null, "denied": false, "error": "%s"}]}
                """.formatted(error));
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of,
                List::of));
        GroupsPane.Bounds narrow = new GroupsPane.Bounds(0, 0, 240, 300);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), narrow, frame.targets);
        List<DrawnFrame.Text> lines = frame.drawn().stream().filter(text -> text.color() == RED
                && !text.text().equals("✗")).toList();
        assertEquals(error, lines.stream().map(DrawnFrame.Text::text).collect(Collectors.joining(" ")),
                "the error's lines read the whole error: " + lines);
        assertTrue(lines.size() > 1, "the error wraps: " + lines);
        assertEquals(List.of(), lines.stream().filter(line -> line.x() + line.text().length() * 6 > narrow.right())
                .toList(), "a line past the row's right edge");
    }

    private static final int FOLLOWED_ITEMS = 40;
    private static final GroupsPane.Bounds SHORT_VIEW = new GroupsPane.Bounds(0, 0, 500, 300);
    private static final GroupsPane.Bounds TALL_VIEW = new GroupsPane.Bounds(0, 0, 500, 8000);
    private static final String FIVE_TYPES = "{\\\"glow\\\": 4, \\\"leaf\\\": 2, \\\"metal\\\": 1, \\\"rock\\\": 3, "
            + "\\\"blaze\\\": 5}";

    private static String itemName(int index) {
        return "Item %02d".formatted(index);
    }

    // An Items pane over forty items whose rows differ in height: a third valued by no entry, a third by one type and
    // a third by five.
    private static ValuesPane manyItems() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(IntStream.range(0, FOLLOWED_ITEMS).mapToObj(index ->
                new ManagerEntrySource.ItemName("minecraft:item_%02d".formatted(index), itemName(index))).toList(),
                List.of()));
        String members = IntStream.range(0, FOLLOWED_ITEMS).filter(index -> index % 3 != 0).mapToObj(index -> """
                {"key": "item_%02d", "keySeparator": null, "kind": "VALUE", "text": "%s", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}"""
                .formatted(index, index % 3 == 1 ? "{\\\"glow\\\": 4}" : FIVE_TYPES))
                .collect(Collectors.joining(", "));
        values.readModel("{\"path\": \"base_values.json\", \"members\": [" + members + "]}");
        String resolved = IntStream.range(0, FOLLOWED_ITEMS).filter(index -> index % 3 != 0).mapToObj(index ->
                "{\"key\": \"item_%02d\", \"value\": %s, \"denied\": false}".formatted(index, index % 3 == 1
                        ? "{\"glow\": \"4\"}"
                        : "{\"glow\": \"123456789\", \"leaf\": \"123456789\", \"metal\": \"123456789\", "
                                + "\"rock\": \"123456789\", \"blaze\": \"123456789\"}"))
                .collect(Collectors.joining(", "));
        values.readResolved("{\"entries\": [" + resolved + "]}");
        return manyItems(values, mock(ManagerActions.class));
    }

    private static ValuesPane manyItems(ValuesTab values, ManagerActions actions) {
        ItemIcons icons = mock(ItemIcons.class);
        return new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    /**
     * A follow into the Items pane empties a search that hides its target and draws the target's row (decision
     * following-a-link-clears-the-panes-search-bar).
     */
    @Test
    void revealClearsTheSearchAndDrawsTheTarget() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(IntStream.range(0, FOLLOWED_ITEMS).mapToObj(index ->
                new ManagerEntrySource.ItemName("minecraft:item_%02d".formatted(index), itemName(index))).toList(),
                List.of()));
        ManagerActions actions = mock(ManagerActions.class);
        List<Object> widgets = new java.util.ArrayList<>();
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ValuesPane pane = manyItems(values, actions);
        pane.init(DrawnFrame.font());
        pane.show(true);
        net.minecraft.client.gui.components.EditBox search = widgets.stream()
                .filter(net.minecraft.client.gui.components.EditBox.class::isInstance)
                .map(net.minecraft.client.gui.components.EditBox.class::cast)
                .filter(box -> "Search items".equals(box.getMessage().getString())).findFirst().orElseThrow();
        search.setValue(itemName(3));
        int target = 25;
        DrawnFrame searched = new DrawnFrame();
        pane.extract(searched.graphics, DrawnFrame.font(), SHORT_VIEW, searched.targets);
        assertFalse(searched.texts().contains(itemName(target)), "the search hides the target");
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertEquals("", search.getValue());
        assertTrue(frame.texts().contains(itemName(target)), frame.texts().toString());
    }

    private static final int LARGE_LIST = 3000;
    private static final int LARGE_GROUPS = 40;
    private static final int GROUP_SIZE = 25;
    // A frame scanning every row ran 3.5 seconds and an indexed one under 25 ms alone; the whole suite's load slows
    // the indexed frame past 80 ms, so the budget sits well clear of that and well under the scan.
    private static final long FRAME_BUDGET_MS = 250;
    private static final int WARM_FRAMES = 8;

    // An Items pane the size of a modded game's: thousands of items, each listed item valued by its own entry and a
    // group of items valued by each group entry.
    private static ValuesPane largeList() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(IntStream.range(0, LARGE_LIST).mapToObj(index ->
                new ManagerEntrySource.ItemName("minecraft:large_%04d".formatted(index), "Large %04d".formatted(index)))
                .toList(), List.of()));
        String own = IntStream.range(0, LARGE_LIST / 2).mapToObj(index -> """
                {"key": "large_%04d", "keySeparator": null, "kind": "VALUE", "text": "\\"%d $base\\"", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}""".formatted(index, index))
                .collect(Collectors.joining(", "));
        String groupEntries = IntStream.range(0, LARGE_GROUPS).mapToObj(group -> """
                {"key": "#group_%d", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 4}", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}""".formatted(group))
                .collect(Collectors.joining(", "));
        String groups = IntStream.range(0, LARGE_GROUPS).mapToObj(group -> """
                {"key": "group_%d", "keySeparator": null, "kind": "VALUE", "text": "[%s]", "entries": [],
                 "leading": null, "trailing": null, "closingGap": null}""".formatted(group,
                IntStream.range(0, GROUP_SIZE).mapToObj(member -> "\\\"large_%04d\\\"".formatted(
                        LARGE_LIST / 2 + group * GROUP_SIZE + member)).collect(Collectors.joining(", "))))
                .collect(Collectors.joining(", "));
        values.readModel("{\"path\": \"base_values.json\", \"members\": [" + own + ", " + groupEntries
                + ", {\"key\": \"_groups\", \"kind\": \"SECTION\", \"text\": \"\", \"entries\": [" + groups + "]}]}");
        values.readResolved("{\"entries\": [" + IntStream.range(0, LARGE_LIST).mapToObj(index ->
                "{\"key\": \"large_%04d\", \"value\": {\"glow\": \"%d\"}, \"denied\": false}".formatted(index, index + 1))
                .collect(Collectors.joining(", ")) + "]}");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        return new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    /**
     * A frame of the Items list over thousands of items draws within a frame budget, its fastest of several warm
     * frames graded so a loaded machine does not fail it: every click waits on the next frame.
     */
    @Test
    void largeItemsListDrawsAFrameWithinBudget() {
        ValuesPane pane = largeList();
        Font font = DrawnFrame.font();
        long fastest = Long.MAX_VALUE;
        for (int run = 0; run < WARM_FRAMES; run++) {
            DrawnFrame frame = new DrawnFrame();
            long started = System.nanoTime();
            pane.extract(frame.graphics, font, SHORT_VIEW, frame.targets);
            fastest = Math.min(fastest, (System.nanoTime() - started) / 1_000_000);
        }
        assertTrue(fastest < FRAME_BUDGET_MS, "fastest frame took " + fastest + " ms over " + LARGE_LIST + " items");
    }

    // Each row's height, read off a list tall enough to draw every row unscrolled.
    private static List<Integer> rowHeights() {
        DrawnFrame tall = new DrawnFrame();
        manyItems().extract(tall.graphics, DrawnFrame.font(), TALL_VIEW, tall.targets);
        List<Integer> tops = tall.topsOf(IntStream.range(0, FOLLOWED_ITEMS).mapToObj(ValuesPaneTest::itemName)
                .toList());
        return IntStream.range(0, FOLLOWED_ITEMS - 1).mapToObj(index -> tops.get(index + 1) - tops.get(index))
                .toList();
    }

    /**
     * A shift + left follow to an item well below the fold scrolls the Items list to the whole row that puts the item
     * nearest the list's middle, by the rows' drawn heights (decision
     * shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void revealCentersTheItemsRowByTheRowsHeights() {
        List<Integer> heights = rowHeights();
        assertTrue(heights.stream().distinct().count() > 1, "rows all " + heights.getFirst() + " tall");
        ValuesPane pane = manyItems();
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int nameBelowRowTop = first.find(itemName(0)).y() - first.firstScissor()[1];
        int target = 25;
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        int[] list = frame.firstScissor();
        int rowTop = frame.find(itemName(target)).y() - nameBelowRowTop;
        double rowMiddle = rowTop + heights.get(target) / 2.0;
        double listMiddle = (list[1] + list[3]) / 2.0;
        assertTrue(Math.abs(rowMiddle - listMiddle) <= 1, rowMiddle + " vs the list's middle " + listMiddle);
    }

    /**
     * The frame after a follow centers an Items row fills the row's bounds in the glow color, and once the glow's
     * frames have passed no frame fills it (decision followed-target-glows-briefly).
     */
    @Test
    void revealedItemsRowGlowsThenStops() {
        List<Integer> heights = rowHeights();
        ValuesPane pane = manyItems();
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int target = 25;
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertGlowCovers(frame, frame.find(itemName(target)), heights.get(target));
        for (int extracted = 1; extracted < RevealGlow.FRAMES; extracted++) {
            DrawnFrame fading = new DrawnFrame();
            pane.extract(fading.graphics, DrawnFrame.font(), SHORT_VIEW, fading.targets);
        }
        DrawnFrame after = new DrawnFrame();
        pane.extract(after.graphics, DrawnFrame.font(), SHORT_VIEW, after.targets);
        assertNoGlow(after);
    }

    // One glow fill spans the list's width and holds the row's name, as tall as the row.
    static void assertGlowCovers(DrawnFrame frame, DrawnFrame.Text name, int rowHeight) {
        List<int[]> glows = frame.fillsOf(Palette.GLOW);
        assertEquals(1, glows.size(), "glow fills: " + glows.size());
        int[] glow = glows.getFirst();
        assertTrue(glow[1] <= name.y() && name.y() < glow[3], java.util.Arrays.toString(glow) + " vs " + name);
        assertEquals(rowHeight, glow[3] - glow[1]);
        assertEquals(frame.firstScissor()[0], glow[0], "the glow starts at the list's left");
    }

    static void assertNoGlow(DrawnFrame frame) {
        for (int age = 0; age < RevealGlow.FRAMES; age++) {
            assertEquals(List.of(), frame.fillsOf(RevealGlow.colorAt(age)), "a glow at age " + age);
        }
    }

    /**
     * A builder opened on a row taller than the Items list scrolls, wheel notch by wheel notch, until its Cancel and
     * Done stand inside the list (decision items-list-scrolls-by-pixel).
     */
    @Test
    void openBuilderTallerThanTheListScrollsItsDoneIntoView() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:gravel", "Gravel")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "gravel", "keySeparator": null, "kind": "VALUE", "text": "\\"2\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.getArgument(0));
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        pane.init(DrawnFrame.font());
        pane.show(true);
        GroupsPane.Bounds tall = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame closed = new DrawnFrame();
        pane.extract(closed.graphics, DrawnFrame.font(), tall, closed.targets);
        closed.click("2");
        DrawnFrame open = new DrawnFrame();
        pane.extract(open.graphics, DrawnFrame.font(), tall, open.targets);
        open.click("2");
        ArgumentCaptor<net.minecraft.client.gui.components.AbstractWidget> focused =
                ArgumentCaptor.forClass(net.minecraft.client.gui.components.AbstractWidget.class);
        verify(actions, atLeastOnce()).focus(focused.capture());
        ((net.minecraft.client.gui.components.EditBox) focused.getValue()).setValue("3");
        assertTrue(pane.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER), "no number box stood open");
        GroupsPane.Bounds squat = new GroupsPane.Bounds(0, 0, 500, 50);
        for (int notch = 0; notch < 20; notch++) {
            DrawnFrame scrolled = new DrawnFrame();
            pane.extract(scrolled.graphics, DrawnFrame.font(), squat, scrolled.targets);
            pane.scroll(0, 0, -1);
        }
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), squat, frame.targets);
        int[] list = frame.firstScissor();
        for (String label : List.of("✓ Done", "✗ Cancel")) {
            DrawnFrame.Text button = frame.find(label);
            assertTrue(button.y() >= list[1] && button.y() + LINE_HEIGHT <= list[3],
                    button + " outside the list " + java.util.Arrays.toString(list));
        }
    }

    /**
     * Opening the builder on an item with no entry, adding a type row and pressing Cancel leaves the draft with no
     * entry for the item, and its row reads Value: Not explicitly set again (decision
     * cancelling-a-new-entry-leaves-nothing).
     */
    @Test
    void cancellingANewEntryLeavesTheItemWithNoEntry() {
        ManagerDraft draft = new ManagerDraft();
        ValuesTab values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:gravel", "Gravel")), List.of()));
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        GroupsPane.Bounds view = new GroupsPane.Bounds(0, 0, 500, 300);
        DrawnFrame closed = new DrawnFrame();
        pane.extract(closed.graphics, DrawnFrame.font(), view, closed.targets);
        closed.click(ReadValueButton.NOT_SET);
        DrawnFrame open = new DrawnFrame();
        pane.extract(open.graphics, DrawnFrame.font(), view, open.targets);
        open.click("+?");
        DrawnFrame picking = new DrawnFrame();
        pane.extract(picking.graphics, DrawnFrame.font(), view, picking.targets);
        pickType(picking, "metal");
        assertTrue(values.hasEntry("minecraft:gravel"), "the type row wrote an entry");
        DrawnFrame changed = new DrawnFrame();
        pane.extract(changed.graphics, DrawnFrame.font(), view, changed.targets);
        changed.click("✗ Cancel");
        assertTrue(!values.hasEntry("minecraft:gravel"), "the cancelled entry stands in the draft");
        DrawnFrame after = new DrawnFrame();
        pane.extract(after.graphics, DrawnFrame.font(), view, after.targets);
        assertTrue(after.texts().contains(ReadValueButton.NOT_SET), after.texts().toString());
    }

    /**
     * A derived item whose override is marked for removal draws its recipe equivalency, the value it takes once the
     * save applies the removal, beside the gold restore arrow; an item whose entry is empty draws no ✗ and no
     * restore arrow, marked or not, and reads Value: Not explicitly set (decision
     * removed-entry-shows-the-derived-value-and-no-restore-on-empty).
     */
    @Test
    void markedOverrideDrawsItsDerivedValueAndAnEmptyEntryOffersNoRestore() {
        ManagerDraft draft = new ManagerDraft();
        ValuesTab values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(List.of(
                new ManagerEntrySource.ItemName("minecraft:oak_planks", "Oak Planks"),
                new ManagerEntrySource.ItemName("minecraft:gravel", "Gravel")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "oak_planks", "keySeparator": null, "kind": "VALUE", "text": "\\"5\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "gravel", "keySeparator": null, "kind": "VALUE", "text": "\\"\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_planks", "value": {"glow": "5"}, "denied": false}]}
                """);
        values.readDerivedFrom("""
                {"items": {"minecraft:oak_planks": {"inputs": [{"id": "minecraft:oak_log", "count": 1}],
                  "output": 4}}}
                """);
        values.markRemoval("minecraft:oak_planks");
        values.markRemoval("minecraft:gravel");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(mock(Matrix3x2fStack.class));
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        DrawnFrame.Text planks = frame.find("Oak Planks");
        ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
        verify(frame.graphics, atLeastOnce()).item(any(), anyInt(), ys.capture());
        long headSprites = ys.getAllValues().stream().filter(y -> Math.abs(y - planks.y()) < LINE_HEIGHT).count();
        // Decision items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right: the planks' own sprite
        // before its "=" and the log input at the far right.
        assertEquals(2, headSprites, "the planks sprite and the log input");
        assertEquals(1, frame.count(ButtonPainter.RESTORE), frame.texts().toString());
        DrawnFrame.Text gravel = frame.find("Gravel");
        // Gravel lists before Oak Planks by name, so an arrow under the planks' name is the planks'.
        assertTrue(gravel.y() < planks.y() && frame.find(ButtonPainter.RESTORE).y() > planks.y(),
                "the one restore arrow is the planks'");
        assertTrue(frame.drawn().stream().noneMatch(text -> text.text().equals("✗")), frame.drawn().toString());
        assertTrue(frame.drawn().stream().anyMatch(text -> text.text().equals(ReadValueButton.NOT_SET)
                && text.y() > gravel.y()), frame.drawn().toString());
    }

    /**
     * An Items row draws its goo value's type icons left to right highest amount first, ties by type key, whatever
     * order the resolve reply held them in (decision value-lists-order-highest-first-ties-by-type-key).
     */
    @Test
    void itemsRowDrawsItsValueRunsHighestFirstTiesByTypeKey() {
        ValuesTab values = new ValuesTab(new ManagerDraft());
        values.readEntries(new ListedEntrySource(List.of(
                new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"5\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "stick", "value": {"metal": "1/2", "weird": "3", "pulse": "3"},
                              "denied": false}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(mock(Matrix3x2fStack.class));
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 300), frame.targets);
        List<String> typeKeys = List.of("pulse", "weird", "metal").stream().map(GooTypeIcons::hoverKey).toList();
        List<Integer> lefts = typeKeys.stream().map(key -> leftOfHover(frame, key)).toList();
        assertEquals(lefts.stream().sorted().toList(), lefts, "pulse, weird, metal from left to right: " + lefts);
    }

    // The left edge of the first area across the screen whose hover names the key.
    private static int leftOfHover(DrawnFrame frame, String key) {
        for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x++) {
            for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y++) {
                if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                    return x;
                }
            }
        }
        throw new AssertionError(key + " not drawn: " + frame.targets.hoverKeys());
    }

    // Clicks the type picker's cell for a goo type, found by its hover across the screen.
    private static void pickType(DrawnFrame frame, String type) {
        String key = GooTypeIcons.hoverKey(type);
        for (int y = 0; y < DrawnFrame.SCREEN_HEIGHT; y += 2) {
            for (int x = 0; x < DrawnFrame.SCREEN_WIDTH; x += 2) {
                if (frame.targets.hovered(x, y).filter(key::equals).isPresent()) {
                    assertTrue(frame.targets.click(x, y));
                    return;
                }
            }
        }
        throw new AssertionError(type + " not in the picker: " + frame.targets.hoverKeys());
    }

    /**
     * One wheel notch moves the Items list by the pixel step, not by a row (decision items-list-scrolls-by-pixel).
     */
    @Test
    void wheelNotchMovesTheItemsListByThePixelStep() {
        GroupsPane.Bounds view = new GroupsPane.Bounds(0, 0, 500, 600);
        ValuesPane pane = manyItems();
        DrawnFrame before = new DrawnFrame();
        pane.extract(before.graphics, DrawnFrame.font(), view, before.targets);
        int notches = 3;
        for (int notch = 0; notch < notches; notch++) {
            pane.scroll(0, 0, -1);
        }
        DrawnFrame after = new DrawnFrame();
        pane.extract(after.graphics, DrawnFrame.font(), view, after.targets);
        String row = itemName(5);
        assertEquals(notches * ValuesPane.SCROLL_STEP, before.find(row).y() - after.find(row).y());
    }

    /**
     * A shift + left follow to an item whose row stands taller than the Items list shows that row first, from its top
     * (decision tall-follow-target-shows-from-its-top).
     */
    @Test
    void revealOfARowTallerThanTheListShowsItFirst() {
        List<Integer> heights = rowHeights();
        int target = 26;
        GroupsPane.Bounds squat = new GroupsPane.Bounds(0, 0, 500, 70);
        ValuesPane pane = manyItems();
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), squat, first.targets);
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), squat, frame.targets);
        int[] list = frame.firstScissor();
        assertTrue(heights.get(target) > list[3] - list[1], "row " + target + " fits the list");
        int nameBelowRowTop = first.find(itemName(0)).y() - first.firstScissor()[1];
        assertEquals(list[1], frame.find(itemName(target)).y() - nameBelowRowTop);
    }

    /**
     * A shift + left follow to the last item stops the Items list at its end, the last first-shown row that fills the
     * view (decision shift-follow-centers-the-target-in-its-scrolling-view).
     */
    @Test
    void revealOfTheLastItemStopsAtTheListsEnd() {
        ValuesPane pane = manyItems();
        DrawnFrame first = new DrawnFrame();
        pane.extract(first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        pane.reveal("minecraft:item_%02d".formatted(FOLLOWED_ITEMS - 1));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertTrue(pane.listBar().maximum() > 0);
        assertEquals(pane.listBar().maximum(), pane.listBar().offset());
    }

    // A Values pane over one torch in a group named lights holding the members given, the torch first.
    private static ValuesPane groupedPane(ManagerActions actions, List<String> members) {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:torch", "Torch")), List.of()));
        String listed = members.stream().map(member -> "\\\"" + member + "\\\"")
                .collect(java.util.stream.Collectors.joining(", "));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "lights", "keySeparator": null, "kind": "VALUE", "text": "[%s]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#lights", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 4}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """.formatted(listed));
        ItemIcons icons = mock(ItemIcons.class);
        return new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    /**
     * A grouped item's equality line draws its group's name as a bordered button, and clicking it opens the Groups
     * tab on that group (decisions items-row-second-line-is-the-equality-expression,
     * blue-text-options-draw-as-bordered-buttons).
     */
    @Test
    void groupedRowLinksItsGroupName() {
        ManagerActions actions = mock(ManagerActions.class);
        ValuesPane pane = groupedPane(actions, List.of("torch"));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        DrawnFrame.Text label = frame.find("#lights");
        int left = label.x() - ButtonPainter.PADDING;
        int top = label.y() - 3;
        int right = left + ButtonPainter.width(DrawnFrame.font(), "#lights");
        verify(frame.graphics).fill(left, top, right, top + ButtonPainter.HEIGHT, Palette.BORDER);
        frame.shiftClick("#lights");
        verify(actions, atLeastOnce()).openGroup("lights");
    }

    /**
     * A grouped row's group name draws underlined while shift is held and plain while it is up (decision
     * holding-shift-underlines-every-link).
     */
    @Test
    void groupNameUnderlinesWhileShiftIsHeld() {
        ValuesPane pane = groupedPane(mock(ManagerActions.class), List.of("torch"));
        DrawnFrame held = new DrawnFrame().holdingShift();
        pane.extract(held.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), held.targets);
        assertTrue(held.underlined("#lights"));
        DrawnFrame up = new DrawnFrame();
        pane.extract(up.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), up.targets);
        assertTrue(!up.underlined("#lights"));
    }

    /**
     * A shift + left click on a grouped row's group name opens the Groups tab on that group, and a plain left click on
     * it passes to the row, which selects the item and opens no builder (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void groupNameFollowsOnShiftClickAndSelectsItsRowOnPlainClick() {
        ManagerActions actions = mock(ManagerActions.class);
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:torch", "Torch")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "lights", "keySeparator": null, "kind": "VALUE", "text": "[\\"torch\\"]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#lights", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 4}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        ValuesPane pane = new ValuesPane(values, icons, actions, builder);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertTrue(values.selectedRow().isEmpty());
        frame.shiftClick("#lights");
        verify(actions).openGroup("lights");
        assertTrue(values.selectedRow().isEmpty());
        frame.click("#lights");
        verify(actions).openGroup("lights");
        assertEquals("minecraft:torch", values.selectedRow().orElseThrow().id());
        assertTrue(!builder.isOpen());
    }

    /**
     * A sapling the #saplings entry values draws "=" and the tag's name as its link, and the link's press, carried
     * to the Groups pane as the manager carries it, selects the saplings tag referent (decision
     * items-row-valued-by-a-tag-reads-the-tag-as-its-group).
     */
    @Test
    void tagValuedRowLinksItsTagReferent() {
        ManagerDraft draft = new ManagerDraft();
        ValuesTab values = new ValuesTab(draft);
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:oak_sapling", "Oak Sapling")),
                List.of(new ManagerEntrySource.ItemTag("minecraft:saplings", List.of("minecraft:oak_sapling")))));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "#saplings", "keySeparator": null, "kind": "VALUE", "text": "\\"$log / 2\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertTrue(frame.texts().contains("="), frame.texts().toString());
        frame.shiftClick("#saplings");
        verify(actions, atLeastOnce()).openGroup("saplings");

        GroupsTab groups = new GroupsTab(draft);
        groups.readDescribed("""
                {"groups": [{"name": "saplings", "kind": "tag",
                             "members": [{"id": "minecraft:oak_sapling", "known": true}],
                             "base": "$log / 2", "tweaks": [], "preview": []}]}
                """);
        new GroupsPane(groups, values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of))
                .reveal("saplings");
        GroupsTab.Group selected = groups.selected().orElseThrow();
        assertEquals("saplings", selected.name());
        assertTrue(selected.tag());
    }

    /**
     * Hovering the group button previews its group (decisions group-link-hovers-member-icons-then-plus-n,
     * group-referent-previews-shorthand-members-then-its-value).
     */
    @Test
    void groupButtonHoversItsGroupsPreview() {
        ValuesPane pane = groupedPane(mock(ManagerActions.class), List.of("minecraft:torch"));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        DrawnFrame.Text label = frame.find("#lights");
        assertEquals(java.util.Optional.of(GroupHovers.hoverKey("lights")),
                frame.targets.hovered(label.x() + 1, label.y() + 1));
    }

    /**
     * The recipe equivalency spaces each input sprite with a small + between inputs and ends on the last input at any
     * output count; a hanging sign's row draws the chain decorated 1/3, the stripped log undecorated, one + and no
     * " / n" run (decisions recipe-equivalency-reads-output-equals-grouped-inputs-over-n,
     * items-row-is-one-line-then-its-expression-inline).
     */
    @Test
    void equivalencySpacesInputsWithPlusesAtAnyOutput() {
        int left = 100;
        int plusWidth = 6;
        int gap = ValuesPane.EQUIVALENCY_GAP;
        ValuesPane.Equivalency two = ValuesPane.layEquivalency(left, 2, plusWidth);
        int second = left + RowLayout.SPRITE_SIZE + gap + plusWidth + gap;
        assertEquals(List.of(left, second), two.spriteLefts());
        assertEquals(List.of(left + RowLayout.SPRITE_SIZE + gap), two.plusLefts());
        ValuesPane.Equivalency single = ValuesPane.layEquivalency(left, 1, plusWidth);
        assertEquals(List.of(left), single.spriteLefts());
        assertEquals(List.of(), single.plusLefts());

        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:oak_hanging_sign", "Oak Hanging Sign")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": []}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_hanging_sign", "value": {"leaf": "8"}, "denied": false}]}
                """);
        values.readDerivedFrom("""
                {"items": {"minecraft:oak_hanging_sign": {"inputs": [{"id": "minecraft:chain", "count": 2},
                  {"id": "minecraft:stripped_oak_log", "count": 6}], "output": 6}}}
                """);
        values.select("minecraft:oak_hanging_sign");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        ArgumentCaptor<String> decorations = ArgumentCaptor.forClass(String.class);
        verify(frame.graphics, atLeast(0)).itemDecorations(any(Font.class), any(), anyInt(), anyInt(),
                decorations.capture());
        assertEquals(List.of(), decorations.getAllValues());
        for (String part : List.of("1", "3")) {
            assertEquals(1, frame.texts().stream().filter(part::equals).count(), part);
        }
        assertEquals(1, frame.texts().stream().filter("+"::equals).count());
        assertTrue(frame.texts().stream().noneMatch(text -> text.matches("\\s*/\\s*\\d+")), frame.texts().toString());
    }

    /**
     * An item the solver values backward draws its inverted recipe, the solving recipe's output less each other input
     * with a minus between, and never a bare "derived" (decisions
     * items-rows-read-valued-derived-or-no-value-with-values-inline,
     * derivation-solves-whichever-side-of-a-recipe-is-unknown).
     */
    @Test
    void solvedItemDrawsItsInvertedRecipe() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:poppy", "Poppy")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": []}
                """);
        values.readResolved("""
                {"entries": [{"key": "poppy", "value": {"zoo": "5"}, "denied": false}]}
                """);
        values.readDerivedFrom("""
                {"items": {"minecraft:poppy": {"inputs": [], "output": 1,
                  "solvedFrom": {"id": "minecraft:suspicious_stew", "count": 1},
                  "less": [{"id": "minecraft:bowl", "count": 1}, {"id": "minecraft:brown_mushroom", "count": 1}]}}}
                """);
        assertEquals(new ValuesTab.Recipe(List.of(), 1, new ValuesTab.Input("minecraft:suspicious_stew", 1),
                List.of(new ValuesTab.Input("minecraft:bowl", 1), new ValuesTab.Input("minecraft:brown_mushroom", 1))),
                values.derivedFrom("minecraft:poppy"));
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertEquals(2, frame.texts().stream().filter("-"::equals).count(), frame.texts().toString());
        assertTrue(frame.texts().stream().noneMatch("+"::equals), frame.texts().toString());
        assertTrue(frame.texts().stream().noneMatch("derived"::equals), frame.texts().toString());
    }

    /**
     * An input's label reads its summed count over the recipe's output as a reduced mixed number, and nothing where
     * that equals 1 (decision recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     */
    @Test
    void inputCountDrawsAsItsFractionOfTheOutput() {
        assertEquals(Optional.of("1/3"), ValuesPane.countLabel(2, 6));
        assertEquals(Optional.empty(), ValuesPane.countLabel(6, 6));
        assertEquals(Optional.of("2 1/4"), ValuesPane.countLabel(9, 4));
        assertEquals(Optional.of("2"), ValuesPane.countLabel(2, 1));
        assertEquals(Optional.empty(), ValuesPane.countLabel(1, 1));
    }

    /**
     * A count splits into its whole part, drawn full size as a stack count, and its fraction, drawn at half scale in
     * the sprite's bottom-right corner as its numerator over a bar over its denominator, the stack's right and bottom
     * edges where a stack count's stand; a mixed count's whole stands just left of the stack (decision
     * recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     */
    @Test
    void fractionStacksNumeratorOverABarInTheSpriteCorner() {
        assertEquals(new StackedFractions.Mixed("", "1", "3"), ValuesPane.countParts(2, 6));
        assertEquals(new StackedFractions.Mixed("2", "1", "4"), ValuesPane.countParts(9, 4));
        assertEquals(new StackedFractions.Mixed("2", "", ""), ValuesPane.countParts(2, 1));
        assertEquals(new StackedFractions.Mixed("", "", ""), ValuesPane.countParts(6, 6));
        int spriteLeft = 100;
        int spriteTop = 40;
        ValuesPane.CornerCount placed = ValuesPane.placeCornerCount(spriteLeft, spriteTop, 6, 12);
        StackedFractions.Stack stack = placed.stack();
        assertEquals((spriteLeft + RowLayout.SPRITE_SIZE + 1) * 2, stack.barX() + stack.barWidth());
        assertEquals((spriteTop + RowLayout.SPRITE_SIZE + 1) * 2, stack.denominatorY() + 7);
        assertEquals(spriteLeft - 12 / 2 - 1, placed.wholeX());
    }

    /**
     * An Items row valued 81/4 metal draws its amount as 20 and 1 over 4, never the improper 81/4.
     */
    @Test
    void valueLineDrawsAnImproperAmountAsAMixedStack() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$x\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "stick", "value": {"metal": "81/4"}, "denied": false}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        List<String> texts = frame.texts();
        int at = texts.indexOf("20");
        assertTrue(at >= 0, texts.toString());
        assertEquals(List.of("20", "1", "4"), texts.subList(at, at + 3));
        assertTrue(!texts.contains("81/4"), texts.toString());
        // Decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere.
        assertEquals(SmallDigits.SCALE, frame.placed("20").scale(), "the amount draws through SmallDigits");
    }

    /**
     * A recipe input's whole count draws through SmallDigits in the sprite's corner, smaller than a stack count
     * (decision goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
     */
    @Test
    void recipeCountDrawsThroughSmallDigits() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:oak_hanging_sign", "Oak Hanging Sign")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": []}
                """);
        values.readResolved("""
                {"entries": [{"key": "oak_hanging_sign", "value": {"leaf": "8"}, "denied": false}]}
                """);
        values.readDerivedFrom("""
                {"items": {"minecraft:oak_hanging_sign": {"inputs": [{"id": "minecraft:chain", "count": 7}],
                  "output": 1}}}
                """);
        values.select("minecraft:oak_hanging_sign");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertEquals(SmallDigits.SCALE, frame.placed("7").scale());
    }

    /**
     * A run is its type icon at whatever size the texture gives, its gap a pixel short and the small amount (decisions items-value-line-draws-type-icons-at-eleven-texels,
     * goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
     */
    @Test
    void runIsItsIconBesideItsAmountAtAnySize() {
        Font font = sixPixelFont();
        for (int icon : ICON_SIZES) {
            assertEquals(icon + ValuesPane.RUN_ICON_GAP - 1 + SmallDigits.width(font, "1.5k"),
                    ValuesPane.runWidth(font, "1.5k", icon));
        }
    }
}
