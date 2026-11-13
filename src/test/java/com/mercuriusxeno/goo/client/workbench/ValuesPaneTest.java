package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.joml.Matrix3x2fStack;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
     * one box filled is the filter the tab holds (decisions items-filters-sit-on-their-own-line-as-one-set,
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
    }

    /**
     * A Negative chip stands right of Fractional, sharing its border, red while its count is above zero and the text
     * color at zero (decision items-filter-set-takes-a-negative-chip).
     */
    @Test
    void negativeChipCountsTheNegativeItemsRightOfFractional() {
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 1");
        List<ValuesPane.Chip> chips = ValuesPane.layChips(labels, String::length, LEFT, TOP,
                ValuesTab.Filter.ITEMS).chips();
        assertEquals(List.of(ValuesTab.Filter.ITEMS, ValuesTab.Filter.UNCOVERED, ValuesTab.Filter.FRACTIONAL,
                ValuesTab.Filter.NEGATIVE), chips.stream().map(ValuesPane.Chip::filter).toList());
        assertEquals(chips.get(2).left() + chips.get(2).width() - 1, chips.get(3).left());
        assertEquals(List.of(Palette.DANGER, Palette.TEXT), List.of(
                ValuesPane.chipColor(ValuesTab.Filter.NEGATIVE, 0, 1),
                ValuesPane.chipColor(ValuesTab.Filter.NEGATIVE, 0, 0)));
    }

    /**
     * A Goo chip stands right of Negative, sharing its border, and draws filled while the tab holds the Goo filter
     * (decision goo-chip-switches-the-search-to-goo-type-names).
     */
    @Test
    void gooChipStandsRightOfNegativeAndHoldsTheGooFilter() {
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0", "Goo");
        List<ValuesPane.Chip> chips = ValuesPane.layChips(labels, String::length, LEFT, TOP, ValuesTab.Filter.GOO)
                .chips();
        ValuesPane.Chip goo = chips.get(4);
        ValuesPane.Chip negative = chips.get(3);
        assertEquals(List.of("Goo", ValuesTab.Filter.GOO, true, negative.left() + negative.width() - 1),
                List.of(goo.label(), goo.filter(), goo.filled(), goo.left()));
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
        List<AbstractWidget> widgets = new ArrayList<>();
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
        ScreenDraw.extract(pane, frame.graphics, font, bounds, frame.targets);
        EditBox search = widgets.stream()
                .filter(EditBox.class::isInstance)
                .map(EditBox.class::cast).findFirst().orElseThrow();
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0", "Goo");
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
            ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), PANE, frame.targets);
            return frame;
        }

        // A row's texts, the small amounts a few pixels under its words (decision
        // goo-amounts-draw-smaller-and-a-pixel-lower-everywhere).
        private static List<DrawnFrame.Text> onLine(DrawnFrame frame, DrawnFrame.Text anchor) {
            return frame.drawn().stream().filter(text -> Math.abs(text.y() - anchor.y()) <= AMOUNT_DROP)
                    .sorted(Comparator.comparingInt(DrawnFrame.Text::x)).toList();
        }

        private static List<Integer> itemXsAround(DrawnFrame frame, int y) {
            ArgumentCaptor<Integer> xs = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> ys = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).item(any(), xs.capture(), ys.capture());
            List<Integer> found = new ArrayList<>();
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
                    ArgumentMatchers.anyFloat(), ArgumentMatchers.anyFloat(), anyInt(),
                    anyInt(), anyInt(), anyInt(), anyInt());
            List<Integer> found = new ArrayList<>();
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
            DrawnFrame.Text stick = frame.find("Stick");
            List<Integer> stickSprites = itemXsAround(frame, stick.y());
            assertEquals(1, stickSprites.size(), "an explicit entry's own sprite alone, no recipe piece");
            assertOwnSpriteBetweenNameAndEquals(stick, onLine(frame, stick).get(1), stickSprites.getFirst());
            assertEquals(List.of(valueColumn()), gooIconXsAround(frame, stick.y()), "its value at the column");
        }

        // Decision one-builder-frame-on-every-pane: the builder opens in the read box's place, at the value's indent,
        // short of the entry's x, its panel's overhang reserved above it.
        @Test
        void builderOpensInTheFrameAtTheIndentShortOfTheDelete() {
            DrawnFrame read = frame();
            DrawnFrame.Text wood = read.find("$wood");
            int[] box = read.buttonAround(wood);
            int boxRight = read.buttonAround(read.drawn().stream().filter(text -> text.text().equals(
                    ReadValueButton.PENCIL) && Math.abs(text.y() - wood.y()) <= 2).findFirst().orElseThrow())[2];
            BuilderFrame.Laid laid = BuilderFrame.lay(DrawnFrame.font(), new BuilderFrame.Row(
                    new GroupsPane.Bounds(PANE.left(), box[1], LIST_RIGHT, PANE.bottom()), null, GooAmountGrid.NONE,
                    true));
            assertEquals(List.of(laid.valueX(), laid.boxRight()), List.of(box[0], boxRight), "the read box");
            read.click("$wood");
            int[] panel = frame().fillsOf(Palette.PANEL_FILL).getFirst();
            assertEquals(List.of(laid.valueX() - RowLayout.BOX_MARGIN, laid.boxTop(), laid.boxRight()),
                    List.of(panel[0], panel[1], panel[2]), "the builder's panel");
        }

        // Decision one-lettering-primitive-names-three-text-roles: an entry's head text centers on its 18 px row.
        @Test
        void entryNameCentersOnItsRow() {
            assertEquals(Lettering.full(DrawnFrame.font()).centeredTop(40, RowLayout.SPRITE_ROW_HEIGHT),
                    ValuesPane.headLine(DrawnFrame.font(), 40, LIST_RIGHT).textTop());
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
            int removeBottom = frame.buttonAround(remove)[3];
            ArgumentCaptor<Integer> tops = ArgumentCaptor.forClass(Integer.class);
            verify(frame.graphics, atLeastOnce()).fill(anyInt(), tops.capture(), anyInt(), anyInt(),
                    eq(Palette.SEPARATOR));
            int rule = tops.getAllValues().stream().filter(top -> top >= removeBottom).min(Integer::compare)
                    .orElseThrow(() -> new AssertionError("no rule under " + removeBottom + ": " + tops.getAllValues()));
            assertEquals(RowLayout.RULE_GAP, rule - removeBottom, "the x over the rule at " + rule);
        }

        /**
         * A head lays its recipe ending at the row's right on the value's line where the value leaves it room, and
         * drops it to a second line, still ending at the row's right, where the value would reach it (decision
         * items-row-reads-item-equals-goo-values-then-the-recipe-at-the-far-right).
         */
        @Test
        void theRecipeEndsAtTheRowsRightOnTheValuesLineOrDropsToASecondLine() {
            ValuesPane.HeadPlaces wide = ValuesPane.layHead(0, 40, 300, 100, 40, 50);
            ValuesPane.HeadPlaces narrow = ValuesPane.layHead(0, 40, 110, 60, 40, 30);
            assertEquals(List.of(250, 40, ValuesPane.ROW_HEIGHT, 80, 40 + ValuesPane.ROW_HEIGHT,
                    2 * ValuesPane.ROW_HEIGHT), List.of(wide.recipeLeft(), wide.recipeTop(), wide.height(),
                    narrow.recipeLeft(), narrow.recipeTop(), narrow.height()));
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
        }
    }

    private static final int RED = 0xFFFF5555;

    /**
     * The search box stands 14 tall at the list's top and stretches to the chips, one gap short of the first (decision
     * one-search-row-per-list-with-its-create-control-beside-it).
     */
    @Test
    void searchRowStretchesToTheChips() {
        ManagerActions actions = mock(ManagerActions.class);
        List<AbstractWidget> widgets = new ArrayList<>();
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(new ValuesTab(), icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        pane.init(DrawnFrame.font());
        GroupsPane.Bounds bounds = new GroupsPane.Bounds(0, 0, 500, 200);
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), bounds, frame.targets);
        EditBox search = widgets.stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> "Search items".equals(box.getMessage().getString())).findFirst().orElseThrow();
        List<String> labels = List.of("Items", "Uncovered 0", "Fractional", "Negative 0", "Goo");
        int chips = ValuesPane.chipsLeft(labels, DrawnFrame.font()::width, bounds);
        assertEquals(List.of(bounds.top(), SearchRow.HEIGHT, chips - SearchRow.GAP),
                List.of(search.getY(), search.getHeight(), search.getX() + search.getWidth()));
    }

    /**
     * A chip the filter does not hold frames as a button does, the button border round the button fill (decision
     * one-frame-primitive-draws-every-bordered-box).
     */
    @Test
    void hollowChipDrawsTheButtonFrame() {
        ValuesPane.Chip hollow = ValuesPane.layChips(List.of("Items", "Uncovered 0"), sixPixelFont()::width, 0, 0,
                ValuesTab.Filter.ITEMS).chips().getLast();
        DrawnFrame frame = new DrawnFrame();
        ValuesPane.drawChipFace(frame.graphics, sixPixelFont(), hollow, Palette.TEXT);
        frame.assertOneFrameIn(Frame.BUTTON);
        assertEquals(List.of(hollow.left(), hollow.top()), List.of(frame.fillsOf(Palette.BORDER).getFirst()[0],
                frame.fillsOf(Palette.BORDER).getFirst()[1]));
    }

    /**
     * The selected chip draws its dark label with no drop shadow, so the label contrasts both its light fill and
     * any shadow under it (decision selected-filter-chip-text-contrasts-its-shadow).
     */
    @Test
    void selectedChipLabelDrawsDarkWithNoShadow() {
        ValuesPane.Chip selected = ValuesPane.layChips(List.of("Items", "Uncovered 0"), sixPixelFont()::width, 0, 0,
                ValuesTab.Filter.ITEMS).chips().getFirst();
        DrawnFrame frame = new DrawnFrame();
        Font font = sixPixelFont();
        ValuesPane.drawChipFace(frame.graphics, font, selected, Palette.TEXT);
        verify(frame.graphics).fill(selected.left() + 1, selected.top() + 1, selected.left() + selected.width() - 1,
                selected.top() + selected.height() - 1, Palette.TEXT);
        verify(frame.graphics).text(eq(font), eq("Items"), anyInt(), anyInt(), eq(Palette.CHIP_FILLED_TEXT),
                eq(false));
    }

    private static Font sixPixelFont() {
        Font font = mock(Font.class);
        when(font.width(anyString())).thenAnswer(call -> call.<String>getArgument(0).length() * 6);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        return font;
    }

    // An Items pane over stick and beetroot soup, each valued, and the actions it tells of draft changes.
    private static ValuesPane twoValued(ValuesTab values, ManagerActions actions) {
        values.readEntries(new ListedEntrySource(List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick"),
                new ManagerEntrySource.ItemName("minecraft:beetroot_soup", "Beetroot Soup"),
                new ManagerEntrySource.ItemName("minecraft:gravel", "Gravel")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"$wood\\"", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null},
                  {"key": "beetroot_soup", "keySeparator": null, "kind": "VALUE", "text": "6", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        ItemIcons icons = mock(ItemIcons.class);
        return new ValuesPane(values, icons, actions, new ExpressionBuilderPane(actions, icons, List::of, List::of));
    }

    /**
     * Each value the builder writes back evaluates the draft afresh, so the value the row shows is the value the
     * builder holds.
     */
    @Test
    void eachBuilderChangeEvaluatesTheDraftAfresh() {
        ManagerActions actions = mock(ManagerActions.class);
        ValuesPane pane = twoValued(new ValuesTab(), actions);
        pane.writeBuilt("minecraft:stick", true, "$wood / 8");
        verify(actions).draftChanged();
    }

    /**
     * The builder's write lands on the entry it opened over, whatever row the list selects meanwhile: the operator,
     * editing beetroot soup, saw the soup's value land in beetroot's entry.
     */
    @Test
    void aBuildersWriteLandsOnItsOwnEntryWhicheverRowIsSelected() {
        ValuesTab values = new ValuesTab();
        ValuesPane pane = twoValued(values, mock(ManagerActions.class));
        values.select("minecraft:stick");
        pane.writeBuilt("minecraft:beetroot_soup", true, "-6");
        assertEquals(List.of("$wood", "-6"), List.of(values.rowOf("minecraft:stick").expression(),
                values.rowOf("minecraft:beetroot_soup").expression()));
    }

    /**
     * A blank write over an item that had no entry drops the entry the edit added (decision
     * cancelling-a-new-entry-leaves-nothing).
     */
    @Test
    void aBlankWriteOverAnItemWithNoEntryDropsTheEntry() {
        ValuesTab values = new ValuesTab();
        ValuesPane pane = twoValued(values, mock(ManagerActions.class));
        values.edit("minecraft:gravel", "5");
        pane.writeBuilt("minecraft:gravel", false, "");
        assertFalse(values.hasEntry("minecraft:gravel"));
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
        List<Object> widgets = new ArrayList<>();
        when(actions.addWidget(any())).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        ValuesPane pane = manyItems(values, actions);
        pane.init(DrawnFrame.font());
        pane.show(true);
        EditBox search = widgets.stream()
                .filter(EditBox.class::isInstance)
                .map(EditBox.class::cast)
                .filter(box -> "Search items".equals(box.getMessage().getString())).findFirst().orElseThrow();
        search.setValue(itemName(3));
        String target = "minecraft:item_%02d".formatted(25);
        pane.reveal(target);
        assertEquals("", search.getValue());
        assertTrue(values.listed().stream().anyMatch(entry -> entry.id().equals(target)), "the target lists");
        // Decision every-follow-lands-selected-and-read-only: the followed item stands selected.
        assertEquals(target, values.selectedRow().orElseThrow().id());
    }

    /**
     * A frozen tab answers each item's source from the draft as freeze read it, so a frame over thousands of items
     * reads the draft once; an edit made while frozen stands only once the tab thaws.
     */
    @Test
    void aFrozenTabAnswersSourcesAsFreezeReadThemUntilItThaws() {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "5", "entries": [],
                   "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.freeze();
        values.dropEntry("minecraft:stick");
        ValuesTab.SourceKind frozen = values.sourceOf("minecraft:stick").kind();
        values.thaw();
        assertEquals(List.of(ValuesTab.SourceKind.ENTRY, ValuesTab.SourceKind.NONE), List.of(frozen,
                values.sourceOf("minecraft:stick").kind()));
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
        ScreenDraw.extract(pane, first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int nameBelowRowTop = first.find(itemName(0)).y() - first.firstScissor()[1];
        int target = 25;
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        int[] list = frame.firstScissor();
        int rowTop = frame.find(itemName(target)).y() - nameBelowRowTop;
        double rowMiddle = rowTop + heights.get(target) / 2.0;
        double listMiddle = (list[1] + list[3]) / 2.0;
        assertTrue(Math.abs(rowMiddle - listMiddle) <= 1, rowMiddle + " vs the list's middle " + listMiddle);
    }

    /**
     * The frame after a follow centers an Items row fills the row's bounds in the glow color (decision
     * followed-target-glows-briefly).
     */
    @Test
    void revealedItemsRowGlows() {
        List<Integer> heights = rowHeights();
        ValuesPane pane = manyItems();
        DrawnFrame first = new DrawnFrame();
        ScreenDraw.extract(pane, first.graphics, DrawnFrame.font(), SHORT_VIEW, first.targets);
        int target = 25;
        pane.reveal("minecraft:item_%02d".formatted(target));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), SHORT_VIEW, frame.targets);
        assertGlowCovers(frame, frame.find(itemName(target)), heights.get(target));
    }

    // One glow fill spans the list's width and holds the row's name, as tall as the row.
    static void assertGlowCovers(DrawnFrame frame, DrawnFrame.Text name, int rowHeight) {
        List<int[]> glows = frame.fillsOf(Palette.GLOW);
        assertEquals(1, glows.size(), "glow fills: " + glows.size());
        int[] glow = glows.getFirst();
        assertTrue(glow[1] <= name.y() && name.y() < glow[3], Arrays.toString(glow) + " vs " + name);
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
        ScreenDraw.extract(pane, closed.graphics, DrawnFrame.font(), tall, closed.targets);
        closed.click("2");
        DrawnFrame open = new DrawnFrame();
        ScreenDraw.extract(pane, open.graphics, DrawnFrame.font(), tall, open.targets);
        open.click("2");
        ArgumentCaptor<AbstractWidget> focused =
                ArgumentCaptor.forClass(AbstractWidget.class);
        verify(actions, atLeastOnce()).focus(focused.capture());
        ((EditBox) focused.getValue()).setValue("3");
        assertTrue(pane.keyPressed(GLFW.GLFW_KEY_ENTER), "no number box stood open");
        GroupsPane.Bounds squat = new GroupsPane.Bounds(0, 0, 500, 50);
        for (int notch = 0; notch < 20; notch++) {
            DrawnFrame scrolled = new DrawnFrame();
            ScreenDraw.extract(pane, scrolled.graphics, DrawnFrame.font(), squat, scrolled.targets);
            pane.scroll(0, 0, -1);
        }
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), squat, frame.targets);
        int[] list = frame.firstScissor();
        for (String label : List.of("✓ Done", "✗ Cancel")) {
            DrawnFrame.Text button = frame.find(label);
            assertTrue(button.y() >= list[1] && button.y() + LINE_HEIGHT <= list[3],
                    button + " outside the list " + Arrays.toString(list));
        }
    }

    /**
     * A derived item whose override is marked for removal reads derived, the source it takes once the save applies
     * the removal, and an entry with a blank expression counts as no entry for the x and the restore arrow (decision
     * removed-entry-shows-the-derived-value-and-no-restore-on-empty).
     */
    @Test
    void markedOverrideReadsDerivedAndABlankEntryCountsAsNone() {
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
        ValuesTab.ManagerEntry gravel = values.listed().stream()
                .filter(entry -> entry.id().equals("minecraft:gravel")).findFirst().orElseThrow();
        ValuesTab.ManagerEntry planks = values.listed().stream()
                .filter(entry -> entry.id().equals("minecraft:oak_planks")).findFirst().orElseThrow();
        assertEquals(List.of(ValuesTab.SourceKind.DERIVED, false, true), List.of(
                values.sourceOf("minecraft:oak_planks").kind(), pane.hasNonEmptyEntry(gravel),
                pane.hasNonEmptyEntry(planks)));
    }

    /**
     * One wheel notch moves the Items list by the pixel step, not by a row (decision items-list-scrolls-by-pixel).
     */
    @Test
    void wheelNotchMovesTheItemsListByThePixelStep() {
        ValuesPane pane = manyItems();
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 600),
                frame.targets);
        pane.scroll(0, 0, -1);
        assertEquals(ScrollBar.WHEEL_STEP, pane.listBar().offset());
    }

    // A Values pane over one torch in a group named lights holding the members given, the torch first.
    private static ValuesPane groupedPane(ManagerActions actions, List<String> members) {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:torch", "Torch")), List.of()));
        String listed = members.stream().map(member -> "\\\"" + member + "\\\"")
                .collect(Collectors.joining(", "));
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
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        DrawnFrame.Text label = frame.find("#lights");
        int left = label.x() - ButtonPainter.PADDING;
        int top = label.y() - 3;
        int right = left + ButtonPainter.width(DrawnFrame.font(), "#lights");
        verify(frame.graphics).fill(left, top, right, top + ButtonPainter.HEIGHT, Palette.BORDER);
        frame.shiftClick("#lights");
        verify(actions, atLeastOnce()).openGroup("lights");
    }


    /**
     * Hovering the group button previews its group (decisions group-link-hovers-member-icons-then-plus-n,
     * group-referent-previews-shorthand-members-then-its-value).
     */
    @Test
    void groupButtonHoversItsGroupsPreview() {
        ValuesPane pane = groupedPane(mock(ManagerActions.class), List.of("minecraft:torch"));
        DrawnFrame frame = new DrawnFrame();
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        DrawnFrame.Text label = frame.find("#lights");
        assertEquals(Optional.of(GroupHovers.hoverKey("lights")),
                frame.targets.hovered(label.x() + 1, label.y() + 1));
    }

    /**
     * The recipe equivalency spaces each input sprite with a small + between inputs and ends on the last input at any
     * output count (decisions recipe-equivalency-reads-output-equals-grouped-inputs-over-n,
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
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200), frame.targets);
        assertEquals(2, frame.texts().stream().filter("-"::equals).count(), frame.texts().toString());
    }

    /**
     * A derived row draws an anchors line under its expression: the word anchors, then each anchor as an icon linked
     * through ItemLinks (decision item-value-view-lists-its-anchors).
     */
    @Test
    void derivedRowListsItsAnchorsAsLinkedIcons() {
        String copper = "minecraft:copper_ingot";
        String tin = "minecraft:tin_ingot";
        DrawnFrame derived = anchorsFrame("""
                {"path": "base_values.json", "members": []}
                """);
        assertTrue(derived.texts().contains("anchors"), derived.texts().toString());
        List<String> keys = derived.targets.hoverKeys();
        assertTrue(keys.containsAll(List.of(ItemIcons.hoverKey(copper), ItemIcons.hoverKey(tin))), keys.toString());
    }

    // A bronze rod row made from one bronze ingot, its reply naming copper and tin as its anchors, over the model given.
    private static DrawnFrame anchorsFrame(String model) {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:bronze_rod", "Bronze Rod")), List.of()));
        values.readModel(model);
        values.readResolved("""
                {"entries": [{"key": "bronze_rod", "value": {"metal": "5"}, "denied": false}]}
                """);
        values.readDerivedFrom("""
                {"items": {"minecraft:bronze_rod": {"inputs": [{"id": "minecraft:bronze_ingot", "count": 1}],
                  "output": 1, "anchors": ["minecraft:copper_ingot", "minecraft:tin_ingot"]}}}
                """);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200),
                frame.targets);
        return frame;
    }

    /**
     * An explicitly valued row with a would-be equivalency reads "(vs." then that equivalency, its inputs linked, then
     * ")" on its head line right of the value, the word vs hovering why; one with none leaves that space blank
     * (decision explicit-value-row-reads-vs-its-would-be-derivative).
     */
    @Test
    void explicitRowReadsVsItsWouldBeEquivalency() {
        String ingot = "minecraft:iron_ingot";
        DrawnFrame frame = explicitBlockFrame("""
                {"items": {"minecraft:iron_block": {"wouldBe": {"inputs": [{"id": "minecraft:iron_ingot",
                  "count": 9}], "output": 1}}}}
                """);
        DrawnFrame.Text opened = frame.find("(vs.");
        DrawnFrame.Text closed = frame.find(")");
        DrawnFrame.Text value = frame.find("=");
        assertEquals(value.y(), opened.y(), opened + " vs " + value);
        assertEquals(value.y(), closed.y(), closed + " vs " + value);
        assertTrue(opened.x() > value.x() && closed.x() > opened.x(), opened + " " + closed + " " + value);
        assertTrue(frame.targets.hoverKeys().contains(ItemIcons.hoverKey(ingot)), frame.targets.hoverKeys().toString());
        int vsLeft = opened.x() + DrawnFrame.font().width("(");
        assertEquals(Optional.of(ValuesPane.VS_HOVER_KEY), frame.targets.hovered(vsLeft + 1, opened.y() + 1));
    }

    // An iron block row its own entry values, over the derived-from reply given.
    private static DrawnFrame explicitBlockFrame(String derivedFrom) {
        ValuesTab values = new ValuesTab();
        values.readEntries(new ListedEntrySource(
                List.of(new ManagerEntrySource.ItemName("minecraft:iron_block", "Iron Block")), List.of()));
        values.readModel("""
                {"path": "base_values.json", "members": [
                  {"key": "iron_block", "keySeparator": null, "kind": "VALUE", "text": "{\\"metal\\": 90}",
                   "entries": [], "leading": null, "trailing": null, "closingGap": null}]}
                """);
        values.readResolved("""
                {"entries": [{"key": "iron_block", "value": {"metal": "90"}, "denied": false}]}
                """);
        values.readDerivedFrom(derivedFrom);
        ManagerActions actions = mock(ManagerActions.class);
        ItemIcons icons = mock(ItemIcons.class);
        ValuesPane pane = new ValuesPane(values, icons, actions,
                new ExpressionBuilderPane(actions, icons, List::of, List::of));
        DrawnFrame frame = new DrawnFrame();
        when(frame.graphics.pose()).thenReturn(new Matrix3x2fStack(4));
        ScreenDraw.extract(pane, frame.graphics, DrawnFrame.font(), new GroupsPane.Bounds(0, 0, 500, 200),
                frame.targets);
        return frame;
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
     * A count over the recipe's output splits into its whole part and its fraction under 1, the mixed number the
     * sprite's corner draws (decision recipe-equivalency-reads-output-equals-grouped-inputs-over-n).
     */
    @Test
    void countSplitsIntoItsWholeAndItsFraction() {
        assertEquals(new StackedFractions.Mixed("", "1", "3"), ValuesPane.countParts(2, 6));
        assertEquals(new StackedFractions.Mixed("2", "1", "4"), ValuesPane.countParts(9, 4));
        assertEquals(new StackedFractions.Mixed("2", "", ""), ValuesPane.countParts(2, 1));
        assertEquals(new StackedFractions.Mixed("", "", ""), ValuesPane.countParts(6, 6));
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
