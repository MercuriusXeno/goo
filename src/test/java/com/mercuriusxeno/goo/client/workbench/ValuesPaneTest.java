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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the Values pane's layouts with no Screen: the Items, Tags and Uncovered set above the search box, and an
 * Items row's title line and equality line (decisions items-filters-sit-on-their-own-line-as-one-set,
 * items-row-second-line-is-the-equality-expression).
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
    // The second line's top, under the row's top pixel and its title line.
    private static final int LINE_TWO = TOP + ValuesPane.ROW_PAD + ValuesPane.TITLE_HEIGHT;
    // A row of title and equality line alone: its lines plus a pixel above and below.
    private static final int TWO_LINES = 2 + ValuesPane.TITLE_HEIGHT + ValuesPane.ROW_HEIGHT;

    /**
     * The Items, Uncovered and Fractional boxes stand on one line, each sharing its inner border with the next, the
     * search box sits one line below them, the one box filled is the filter the tab holds, the pane draws no Tags
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
        assertEquals(first.top() + first.height() + ValuesPane.CHIP_LINE_GAP, layout.searchTop());

        Font font = sixPixelFont();
        ValuesTab values = new ValuesTab();
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, font, new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertEquals(List.of(), frame.texts().stream().filter("Tags"::equals).toList());
        ValuesPane.Chip fractional = ValuesPane.layChips(List.of("Items", "Uncovered 0", "Fractional"),
                font::width, 0, 0, values.filter()).chips().get(2);
        frame.targets.click(fractional.left() + 1, fractional.top() + 1);
        assertEquals(ValuesTab.Filter.FRACTIONAL, values.filter());
    }

    private static Font sixPixelFont() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    /**
     * With the builder open over a value in a pane too short to show it whole, the wheel over the detail column
     * scrolls the builder with the rest of the column, so its done control comes into the pane and answers there
     * (decision every-tab-draws-a-scroll-bar).
     */
    @Test
    void detailColumnScrollsTheOpenBuilderToItsDoneControl() {
        Font font = sixPixelFont();
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"1 + 2 + 3 + 4 + 5 + 6\\"",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.select("minecraft:stick");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ExpressionBuilderPane builder = new ExpressionBuilderPane(actions, icons, List::of, List::of);
        ValuesPane pane = new ValuesPane(values, icons, actions, builder);
        builder.open("1 + 2 + 3 + 4 + 5 + 6", typed -> { }, () -> { });
        GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 80);
        ClickTargets targets = new ClickTargets();
        pane.extract(graphics, font, bounds, targets);
        String done = BuilderOverlays.hintKey("No unsaved changes");
        for (int notch = 0; notch < 40; notch++) {
            pane.scroll(400, 0, -1);
        }
        targets.clear();
        pane.extract(graphics, font, bounds, targets);
        boolean doneInPane = false;
        for (int y = bounds.top(); y < bounds.bottom() && !doneInPane; y++) {
            for (int x = bounds.left(); x < bounds.right() && !doneInPane; x += 2) {
                doneInPane = targets.hovered(x, y).filter(done::equals).isPresent();
            }
        }
        assertTrue(doneInPane);
    }

    /**
     * The selected item's page draws a Remove entry button where the file holds its entry, and pressing it drops the
     * entry and has the draft read afresh, so the item falls back to what recipes derive.
     */
    @Test
    void removeEntryButtonDropsTheSelectedEntry() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:netherite_ingot", "Netherite Ingot")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "netherite_ingot", "keySeparator": null, "kind": "VALUE", "text": "\\"$netherite\\"",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.select("minecraft:netherite_ingot");
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        frame.click("Remove entry");
        assertTrue(!values.selectedHasEntry());
        verify(actions).draftChanged();
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
        assertTrue(!frame.rightClick("wood"));
        verify(actions, never()).openVariable(anyString());
        assertTrue(!builder.isOpen());
        frame.shiftClick("wood");
        verify(actions).openVariable("wood");
        assertTrue(!builder.isOpen());
        frame.click("wood");
        assertTrue(builder.isOpen());
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
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        return new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of));
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

    // The first shown row that puts the target row's middle closest to the view's middle, walking up from the row.
    private static int centeredFirstRow(List<Integer> heights, int target, int view) {
        double half = view / 2.0;
        int best = target;
        double above = 0;
        double bestMiss = Math.abs(heights.get(target) / 2.0 - half);
        for (int first = target - 1; first >= 0 && above + heights.get(target) / 2.0 < half; first--) {
            above += heights.get(first);
            double miss = Math.abs(above + heights.get(target) / 2.0 - half);
            if (miss < bestMiss) {
                best = first;
                bestMiss = miss;
            }
        }
        return best;
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
        int target = 25;
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        int[] list = frame.firstScissor();
        int expected = centeredFirstRow(heights, target, list[3] - list[1]);
        assertTrue(expected < target, "the target already stands centered at the top");
        assertEquals(expected, pane.listBar().offset());
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
        DrawnFrame.Text label = frame.find("lights");
        int left = label.x() - ButtonPainter.PADDING;
        int top = label.y() - 3;
        int right = left + ButtonPainter.width(DrawnFrame.font(), "lights");
        verify(frame.graphics).fill(left, top, right, top + ButtonPainter.HEIGHT, ButtonPainter.BORDER);
        frame.shiftClick("lights");
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
        assertTrue(held.underlined("lights"));
        DrawnFrame up = new DrawnFrame();
        pane.extract(up.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), up.targets);
        assertTrue(!up.underlined("lights"));
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
        frame.shiftClick("lights");
        verify(actions).openGroup("lights");
        assertTrue(values.selectedRow().isEmpty());
        frame.click("lights");
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
        frame.shiftClick("saplings");
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
     * Hovering the group button answers a tooltip of the group's members as item icons in member order, eight at
     * most, then ..+n counting the rest; a group of eight or fewer shows every member and no ..+n (decision
     * group-link-hovers-member-icons-then-plus-n).
     */
    @Test
    void groupButtonHoversItsMemberIconsThenPlusN() {
        List<String> eleven = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> index == 0 ? "minecraft:torch" : "minecraft:light" + index).toList();
        assertEquals(new TooltipLine.MemberIcons(eleven.subList(0, 8), "..+3"), hoveredGroupTooltip(eleven));
        List<String> five = eleven.subList(0, 5);
        assertEquals(new TooltipLine.MemberIcons(five, ""), hoveredGroupTooltip(five));
    }

    private static TooltipLine hoveredGroupTooltip(List<String> members) {
        ValuesPane pane = groupedPane(mock(ManagerActions.class), members);
        DrawnFrame frame = new DrawnFrame();
        pane.extract(frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        DrawnFrame.Text label = frame.find("lights");
        String key = frame.targets.hovered(label.x() + 1, label.y() + 1).orElseThrow();
        List<TooltipLine> lines = pane.groupTooltipFor(key).orElseThrow();
        assertEquals(1, lines.size());
        return lines.getFirst();
    }

    /**
     * An item worth five goo types, at a width fitting two runs, lays them across three value lines; layValueRuns
     * answers one row for an empty worth, which the row layout does not count (decisions
     * items-rows-read-valued-derived-or-no-value-with-values-inline, goo-runs-follow-on-their-own-lines-after-line-two).
     */
    @Test
    void valueRunsWrapOntoAsManyRowsAsTheWidthNeeds() {
        int run = 40;
        int width = run + ValuesPane.RUN_GAP + run + 2;
        ValuesPane.ValueRuns five = ValuesPane.layValueRuns(List.of(run, run, run, run, run), width);
        assertEquals(3, five.rows());
        int second = run + ValuesPane.RUN_GAP;
        assertEquals(List.of(new ValuesPane.Spot(0, 0), new ValuesPane.Spot(second, 0), new ValuesPane.Spot(0, 1),
                new ValuesPane.Spot(second, 1), new ValuesPane.Spot(0, 2)), five.spots());
        assertEquals(1, ValuesPane.layValueRuns(List.of(), width).rows());
    }

    // An Items row at LEFT, TOP running to RIGHT, for a font whose glyph line is LINE_HEIGHT tall, its goo type icons
    // iconSize pixels on a side.
    private static ValuesPane.EntryLayout lay(ValuesTab.SourceKind kind, List<Integer> runWidths, int symbolWidth,
                                              int iconSize) {
        return ValuesPane.layEntry(new GroupsPane.Bounds(LEFT, TOP, RIGHT, TOP + 200), runWidths,
                new ValuesPane.RowText(kind, LINE_HEIGHT, symbolWidth, EQUALS_WIDTH), iconSize);
    }

    private static ValuesPane.EntryLayout lay(ValuesTab.SourceKind kind, List<Integer> runWidths) {
        return lay(kind, runWidths, 0, SMALL_ICON);
    }

    /**
     * Each value disposition reads as its own symbol: valued a green check, derived a green equals, grouped a green
     * hash, no value a red question mark, denied a green cancel circle drawn at the check's size, within a pixel, for
     * a font whose check is 8 wide on a 9-pixel line (decisions
     * items-row-title-carries-a-disposition-symbol-at-the-right-edge,
     * denied-symbol-is-a-green-cancel-circle-at-symbol-size).
     */
    @Test
    void eachDispositionReadsAsItsSymbolAndColor() {
        int green = 0xFF80E080;
        ValuesPane.Disposition check = ValuesPane.dispositionOf(ValuesTab.SourceKind.ENTRY);
        assertEquals(new ValuesPane.Disposition("✔", green, false), check);
        assertEquals(new ValuesPane.Disposition("=", green, false),
                ValuesPane.dispositionOf(ValuesTab.SourceKind.DERIVED));
        assertEquals(new ValuesPane.Disposition("#", green, false),
                ValuesPane.dispositionOf(ValuesTab.SourceKind.GROUP));
        assertEquals(new ValuesPane.Disposition("?", ValuesPane.RED, false),
                ValuesPane.dispositionOf(ValuesTab.SourceKind.NONE));
        ValuesPane.Disposition denied = ValuesPane.dispositionOf(ValuesTab.SourceKind.DENIED);
        assertEquals(green, denied.color());
        assertTrue(denied.drawn());
        java.util.function.ToIntFunction<String> width = glyph -> glyph.equals("✔") ? 8 : 4;
        ValuesPane.SymbolSize checkSize = ValuesPane.symbolSize(check, width, LINE_HEIGHT);
        ValuesPane.SymbolSize deniedSize = ValuesPane.symbolSize(denied, width, LINE_HEIGHT);
        assertTrue(Math.abs(checkSize.width() - deniedSize.width()) <= 1, checkSize + " against " + deniedSize);
        assertTrue(Math.abs(checkSize.height() - deniedSize.height()) <= 1, checkSize + " against " + deniedSize);
    }

    /**
     * The symbol stands right-aligned at the row's right edge on the title line, and the name is cut to end a gap
     * before it; a row drawing no symbol takes the line's full width (decision
     * items-row-title-carries-a-disposition-symbol-at-the-right-edge).
     */
    @Test
    void symbolStandsAtTheRightEdgeAndCutsTheName() {
        int symbolWidth = 7;
        ValuesPane.Title title = lay(ValuesTab.SourceKind.ENTRY, List.of(40), symbolWidth, SMALL_ICON).title();
        assertEquals(RIGHT - symbolWidth, title.symbolLeft());
        assertEquals(title.symbolLeft() - ValuesPane.SYMBOL_GAP, title.nameLeft() + title.nameWidth());
        ValuesPane.Title bare = lay(ValuesTab.SourceKind.NONE, List.of()).title();
        assertEquals(RIGHT, bare.nameLeft() + bare.nameWidth());
    }

    /**
     * The title stands on the row's first line a pixel under its top, from the row's left edge, and the sprite leads
     * the second line, top-aligned in it, with the equals sign after it (decisions
     * items-row-second-line-is-the-equality-expression, items-row-pads-a-hair-all-round).
     */
    @Test
    void titleLeadsAndTheSpriteLeadsLineTwo() {
        ValuesPane.EntryLayout layout = lay(ValuesTab.SourceKind.DERIVED, List.of(40));
        assertEquals(LEFT + ValuesPane.ROW_PAD, layout.title().nameLeft());
        assertEquals(TOP + ValuesPane.ROW_PAD, layout.title().nameTop());
        ValuesPane.Equality line = layout.equality();
        assertEquals(LEFT + ValuesPane.ROW_PAD, line.spriteLeft());
        assertEquals(LINE_TWO + 1, line.spriteTop());
        assertEquals(line.spriteLeft() + RowLayout.SPRITE_SIZE + ValuesPane.ICON_GAP, line.equalsLeft());
        assertEquals(line.equalsLeft() + EQUALS_WIDTH + ValuesPane.ICON_GAP, line.besideLeft());
        assertEquals(LINE_TWO + (ValuesPane.ROW_HEIGHT - LINE_HEIGHT) / 2, line.textTop());
    }

    /**
     * A valued item's goo runs start on line 2, right of the sprite and the equals sign, centered on the line, and
     * the row is its two lines plus a pixel above and below (decisions
     * goo-runs-follow-on-their-own-lines-after-line-two, items-row-pads-a-hair-all-round).
     */
    @Test
    void valuedRowRunsBesideItsSpriteOnLineTwo() {
        ValuesPane.EntryLayout layout = lay(ValuesTab.SourceKind.ENTRY, List.of(40, 40));
        assertEquals(layout.equality().besideLeft(), layout.runsLeft());
        int valueLine = SMALL_ICON + 2;
        assertEquals(LINE_TWO + (ValuesPane.ROW_HEIGHT - valueLine) / 2, layout.runsTop());
        assertEquals(TWO_LINES, layout.height());
    }

    /**
     * A valued item whose runs wrap continues them on value lines under its first, the row growing to the last line
     * plus a pixel (decision goo-runs-follow-on-their-own-lines-after-line-two).
     */
    @Test
    void valuedRowWrapsUnderItsFirstValueLine() {
        ValuesPane.EntryLayout layout = lay(ValuesTab.SourceKind.ENTRY, List.of(100, 100, 100));
        int valueLine = SMALL_ICON + 2;
        assertEquals(layout.runsTop() + valueLine, layout.lineTop(1));
        assertEquals(layout.lineTop(1) + valueLine + ValuesPane.ROW_PAD - TOP, layout.height());
    }

    /**
     * A derived or grouped item's goo runs start on line 3 at the row's left edge, and the row is its three lines
     * plus a pixel above and below (decisions goo-runs-follow-on-their-own-lines-after-line-two,
     * items-row-pads-a-hair-all-round).
     */
    @Test
    void derivedAndGroupedRowsRunOnLineThree() {
        for (ValuesTab.SourceKind kind : List.of(ValuesTab.SourceKind.DERIVED, ValuesTab.SourceKind.GROUP)) {
            ValuesPane.EntryLayout layout = lay(kind, List.of(40));
            assertEquals(LEFT + ValuesPane.ROW_PAD, layout.runsLeft());
            assertEquals(LINE_TWO + ValuesPane.ROW_HEIGHT, layout.runsTop());
            assertEquals(TWO_LINES + SMALL_ICON + 2, layout.height());
        }
    }

    /**
     * A no-value or denied row is two lines high: its title and its equality line, plus a pixel above and below
     * (decisions items-row-second-line-is-the-equality-expression, items-row-pads-a-hair-all-round).
     */
    @Test
    void noValueAndDeniedRowsAreTwoLinesHigh() {
        assertEquals(TWO_LINES, lay(ValuesTab.SourceKind.NONE, List.of()).height());
        assertEquals(TWO_LINES, lay(ValuesTab.SourceKind.DENIED, List.of()).height());
    }

    /**
     * The recipe equivalency spaces each input sprite with a small + between inputs and ends on the last input at any
     * output count; a hanging sign's row and detail page draw the chain decorated 1/3, the stripped log undecorated,
     * one + each and no " / n" run (decision recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
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
            assertEquals(2, frame.texts().stream().filter(part::equals).count(), part);
        }
        assertEquals(2, frame.texts().stream().filter("+"::equals).count());
        assertTrue(frame.texts().stream().noneMatch(text -> text.matches("\\s*/\\s*\\d+")), frame.texts().toString());
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
    }

    /**
     * A run is its type icon at whatever size the texture gives, its gap and the amount's width (decision
     * items-value-line-draws-type-icons-at-eleven-texels).
     */
    @Test
    void runIsItsIconBesideItsAmountAtAnySize() {
        Font font = sixPixelFont();
        for (int icon : ICON_SIZES) {
            assertEquals(icon + ValuesPane.RUN_ICON_GAP + font.width("1.5k"), ValuesPane.runWidth(font, "1.5k", icon));
        }
    }

    /**
     * Each value line is its icon plus a pixel above and below, whatever the icon's size: the icon one pixel under the
     * line's top and the amount centered on it (decision value-line-is-as-tall-as-its-icon).
     */
    @Test
    void valueLinesAreTheIconPlusAPixelAboveAndBelow() {
        int run = 150;
        List<Integer> oneLine = List.of(run);
        List<Integer> twoLines = List.of(run, run);
        for (int icon : ICON_SIZES) {
            int line = icon + 2;
            assertEquals(TWO_LINES + line, lay(ValuesTab.SourceKind.DERIVED, oneLine, 0, icon).height());
            ValuesPane.EntryLayout two = lay(ValuesTab.SourceKind.DERIVED, twoLines, 0, icon);
            assertEquals(TWO_LINES + 2 * line, two.height());
            assertEquals(LINE_TWO + ValuesPane.ROW_HEIGHT + line, two.lineTop(1));
            assertEquals(two.lineTop(1) + 1, two.iconTop(1));
            assertEquals(two.lineTop(1) + (line - LINE_HEIGHT) / 2, two.amountTop(1));
        }
    }

    /**
     * The detail page stacks the selected item's card, the Value label a gap under the card's bottom, the value on the
     * line under the label, and the loader error as the last line, which grows the page by one line only when there
     * is an error (decision detail-pane-top-is-the-items-card-verbatim).
     */
    @Test
    void detailPageStacksTheCardTheValueAndTheErrorLast() {
        int cardHeight = TWO_LINES + SMALL_ICON + 2;
        int valueHeight = 30;
        ValuesPane.Page bare = ValuesPane.layPage(TOP, cardHeight, valueHeight, false);
        assertEquals(TOP + cardHeight + ValuesPane.GAP, bare.labelTop());
        assertEquals(bare.labelTop() + ValuesPane.LABEL_LINE_HEIGHT, bare.valueTop());
        assertTrue(bare.errorTop() >= bare.valueTop() + valueHeight);
        ValuesPane.Page erred = ValuesPane.layPage(TOP, cardHeight, valueHeight, true);
        assertEquals(bare.errorTop(), erred.errorTop());
        assertEquals(erred.errorTop() + ValuesPane.LINE_HEIGHT, erred.end());
        assertEquals(bare.end() + ValuesPane.LINE_HEIGHT, erred.end());
    }
}
