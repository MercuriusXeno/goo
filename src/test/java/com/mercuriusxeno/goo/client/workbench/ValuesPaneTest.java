package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the Items, Tags and Uncovered set's layout on its own line above the search box, with no Screen
 * (decision items-filters-sit-on-their-own-line-as-one-set).
 */
class ValuesPaneTest {
    private static final int LEFT = 10;
    private static final int TOP = 30;
    private static final int RIGHT = 300;
    private static final int LINE_HEIGHT = 9;
    // The goo type icons' side today, and the side they widen to.
    private static final int SMALL_ICON = 11;
    private static final int[] ICON_SIZES = {SMALL_ICON, 12};

    /**
     * The three boxes stand on one line, each sharing its inner border with the next, the search box sits one line
     * below them, and the one box filled is the filter the tab holds.
     */
    @Test
    void filtersLayAsOneSetAboveTheSearchBox() {
        ValuesPane.ChipLayout layout = ValuesPane.layChips(List.of("Items", "Tags", "Uncovered 3"), String::length,
                LEFT, TOP, ValuesTab.Filter.TAGS);
        List<ValuesPane.Chip> chips = layout.chips();
        assertEquals(List.of(ValuesTab.Filter.ITEMS, ValuesTab.Filter.TAGS, ValuesTab.Filter.UNCOVERED),
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
    }

    /**
     * With the builder open over a value in a pane too short to show it whole, the wheel over the detail column
     * scrolls the builder with the rest of the column, so its done control comes into the pane and answers there
     * (decision every-tab-draws-a-scroll-bar).
     */
    @Test
    void detailColumnScrollsTheOpenBuilderToItsDoneControl() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        ValuesTab values = new ValuesTab();
        values.readInventory("""
                {"items": [{"id": "minecraft:stick", "name": "Stick"}], "item_tags": []}
                """);
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
     * An item worth five goo types, at a width fitting two runs, lays them across three value lines; layValueRuns
     * answers one row for an empty worth, which the row layout does not count (decisions
     * items-rows-read-valued-derived-or-no-value-with-values-inline, items-goo-type-line-always-on-the-second-row).
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
    private static ValuesPane.EntryLayout lay(List<Integer> runWidths, boolean derived, boolean labelShown,
                                              int iconSize) {
        return ValuesPane.layEntry(new GroupsPane.Bounds(LEFT, TOP, RIGHT, TOP + 200), runWidths, derived,
                new ValuesPane.RowText(labelShown, LINE_HEIGHT), iconSize);
    }

    private static ValuesPane.EntryLayout lay(List<Integer> runWidths, boolean derived, boolean labelShown) {
        return lay(runWidths, derived, labelShown, SMALL_ICON);
    }

    /**
     * An item with a goo value fitting one line draws its runs on a value line under the first 18px row, the first at
     * the name's left edge (decisions items-goo-type-line-always-on-the-second-row, value-line-is-as-tall-as-its-icon).
     */
    @Test
    void valuedRowDrawsItsRunsOnTheSecondRow() {
        ValuesPane.EntryLayout layout = lay(List.of(40, 40), false, true);
        assertEquals(TOP + ValuesPane.ROW_HEIGHT, layout.runsTop());
        assertEquals(layout.nameLeft(), layout.runsLeft());
        assertEquals(new ValuesPane.Spot(0, 0), layout.runs().spots().getFirst());
        assertEquals(ValuesPane.ROW_HEIGHT + SMALL_ICON + 2, layout.height());
    }

    /**
     * The item name is cut to the row's full width from its left edge, not to a column beside the value (decision
     * items-goo-type-line-always-on-the-second-row).
     */
    @Test
    void nameTakesTheFirstRowsFullWidth() {
        ValuesPane.EntryLayout layout = lay(List.of(40), false, true);
        assertEquals(RIGHT - layout.nameLeft(), layout.nameWidth());
    }

    /**
     * An item with no goo value takes no value line: one 18px row high, and two with its derived-from line (decision
     * items-goo-type-line-always-on-the-second-row).
     */
    @Test
    void valuelessRowStaysOneRowHigh() {
        assertEquals(ValuesPane.ROW_HEIGHT, lay(List.of(), false, true).height());
        ValuesPane.EntryLayout derived = lay(List.of(), true, false);
        assertEquals(2 * ValuesPane.ROW_HEIGHT, derived.height());
        assertEquals(TOP + ValuesPane.ROW_HEIGHT, derived.derivedTop());
    }

    /**
     * A run is its type icon at whatever size the texture gives, its gap and the amount's width (decision
     * items-value-line-draws-type-icons-at-eleven-texels).
     */
    @Test
    void runIsItsIconBesideItsAmountAtAnySize() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        for (int icon : ICON_SIZES) {
            assertEquals(icon + ValuesPane.RUN_ICON_GAP + font.width("1.5k"), ValuesPane.runWidth(font, "1.5k", icon));
        }
    }

    /**
     * Each value line is its icon plus a pixel above and below, whatever the icon's size: the icon one pixel under the
     * line's top, the amount centered on it, and the derived-from line right under the last value line (decision
     * value-line-is-as-tall-as-its-icon).
     */
    @Test
    void valueLinesAreTheIconPlusAPixelAboveAndBelow() {
        int run = 100;
        List<Integer> oneLine = List.of(run);
        List<Integer> twoLines = List.of(run, run, run);
        for (int icon : ICON_SIZES) {
            int line = icon + 2;
            assertEquals(ValuesPane.ROW_HEIGHT + line, lay(oneLine, false, true, icon).height());
            assertEquals(ValuesPane.ROW_HEIGHT + 2 * line, lay(twoLines, false, true, icon).height());
            ValuesPane.EntryLayout oneDerived = lay(oneLine, true, false, icon);
            assertEquals(2 * ValuesPane.ROW_HEIGHT + line, oneDerived.height());
            assertEquals(TOP + ValuesPane.ROW_HEIGHT + line, oneDerived.derivedTop());
            ValuesPane.EntryLayout twoDerived = lay(twoLines, true, false, icon);
            assertEquals(2 * ValuesPane.ROW_HEIGHT + 2 * line, twoDerived.height());
            assertEquals(TOP + ValuesPane.ROW_HEIGHT + 2 * line, twoDerived.derivedTop());
            assertEquals(TOP + ValuesPane.ROW_HEIGHT + line, twoDerived.lineTop(1));
            assertEquals(twoDerived.lineTop(1) + 1, twoDerived.iconTop(1));
            assertEquals(twoDerived.lineTop(1) + (line - LINE_HEIGHT) / 2, twoDerived.amountTop(1));
        }
    }

    /**
     * The name centers on the first 18px row when no source label sits under it, and sits at the row's top + 1 over
     * the label otherwise (decision item-name-centers-on-its-row).
     */
    @Test
    void nameCentersWhenNoLabelSitsUnderIt() {
        assertEquals(TOP + (ValuesPane.ROW_HEIGHT - LINE_HEIGHT) / 2, lay(List.of(40), true, false).nameTop());
        assertEquals(TOP + 1, lay(List.of(40), false, true).nameTop());
    }
}
