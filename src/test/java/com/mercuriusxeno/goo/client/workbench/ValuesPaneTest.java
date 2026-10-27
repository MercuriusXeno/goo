package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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
     * A grouped item's equality line draws its group's name as a link, and clicking it opens the Groups tab on that
     * group (decision items-row-second-line-is-the-equality-expression).
     */
    @Test
    void groupedRowLinksItsGroupName() {
        ValuesTab values = new ValuesTab();
        values.readInventory("""
                {"items": [{"id": "minecraft:torch", "name": "Torch"}], "item_tags": []}
                """);
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "_groups", "keySeparator": null, "kind": "OBJECT", "text": "", "entries": [
                     {"key": "lights", "keySeparator": null, "kind": "VALUE", "text": "[\\"torch\\"]", "entries": [],
                      "leading": null, "trailing": null, "closingGap": null}],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "#lights", "keySeparator": null, "kind": "VALUE", "text": "{\\"glow\\": 4}", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        ClickTargets targets = new ClickTargets();
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 200);
        pane.extract(mock(GuiGraphicsExtractor.class), sixPixelFont(), bounds, targets);
        for (int y = bounds.top(); y < bounds.bottom(); y++) {
            for (int x = bounds.left(); x < ValuesPane.SIDEBAR_WIDTH; x++) {
                targets.click(x, y);
            }
        }
        verify(actions, atLeastOnce()).openGroup("lights");
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
     * hash, no value a red question mark, denied a gray cancel circle (decision
     * items-row-title-carries-a-disposition-symbol-at-the-right-edge).
     */
    @Test
    void eachDispositionReadsAsItsSymbolAndColor() {
        int green = 0xFF80E080;
        int gray = 0xFFA0A0A0;
        assertEquals(new ValuesPane.Disposition("✔", green), ValuesPane.dispositionOf(ValuesTab.SourceKind.ENTRY));
        assertEquals(new ValuesPane.Disposition("=", green), ValuesPane.dispositionOf(ValuesTab.SourceKind.DERIVED));
        assertEquals(new ValuesPane.Disposition("#", green), ValuesPane.dispositionOf(ValuesTab.SourceKind.GROUP));
        assertEquals(new ValuesPane.Disposition("?", ValuesPane.RED),
                ValuesPane.dispositionOf(ValuesTab.SourceKind.NONE));
        assertEquals(new ValuesPane.Disposition("⊘", gray), ValuesPane.dispositionOf(ValuesTab.SourceKind.DENIED));
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
}
