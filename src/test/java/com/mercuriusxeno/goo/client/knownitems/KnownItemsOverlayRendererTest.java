package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.Drawn;
import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.ItemIcon;
import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.KnownItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * The overlay's frame is one darkening fill over the whole screen, a search bar's flat box and typed
 * text above the grid, one icon per shown item, a flat track and thumb when the rows overflow, and the
 * hovered item's name beside the cursor, and nothing else; a click on an icon picks that item
 * (decisions menu-is-a-darkening-overlay-not-a-screen, right-click-on-the-plexer-opens-the-known-items-menu,
 * menu-lists-known-items-alphabetically-with-search-and-scroll).
 */
class KnownItemsOverlayRendererTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int LONG_LIST = 200;
    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");
    private static final Identifier SAND = Identifier.withDefaultNamespace("sand");

    private static KnownItem known(Identifier id, String name) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getHoverName()).thenReturn(Component.literal(name));
        return new KnownItem(id, stack);
    }

    private final List<KnownItem> items = List.of(known(STONE, "Stone"), known(DIRT, "Dirt"), known(SAND, "Sand"));
    private final List<KnownItem> longList = IntStream.range(0, LONG_LIST)
            .mapToObj(i -> known(Identifier.withDefaultNamespace("item_" + i), "Item " + i)).toList();

    private static List<Invocation> calls(GuiGraphicsExtractor graphics) {
        return List.copyOf(mockingDetails(graphics).getInvocations());
    }

    private static boolean cancelGlyph(Invocation call) {
        return call.getMethod().getName().equals("fill")
                && (int) call.getArgument(4) == KnownItemsOverlayRenderer.CANCEL_RED;
    }

    /** The colors a frame filled in, the Cancel glyph's own fills left out. */
    private static List<Integer> fillColors(GuiGraphicsExtractor graphics) {
        return callsOf(graphics, "fill").stream().filter(fill -> !cancelGlyph(fill))
                .map(fill -> (Integer) fill.getArgument(4)).toList();
    }

    /** The draws a frame made, the Cancel glyph's own fills left out. */
    private static List<String> methods(GuiGraphicsExtractor graphics) {
        return calls(graphics).stream().filter(call -> !cancelGlyph(call)).map(call -> call.getMethod().getName())
                .toList();
    }

    private static int[] rect(Invocation fill) {
        return new int[] {fill.getArgument(0), fill.getArgument(1), fill.getArgument(2), fill.getArgument(3)};
    }

    private static List<Invocation> callsOf(GuiGraphicsExtractor graphics, String method) {
        return calls(graphics).stream().filter(call -> call.getMethod().getName().equals(method)).toList();
    }

    /** A cursor off the frame, resting on nothing. */
    private static final int NOWHERE = -100;

    private static Drawn render(GuiGraphicsExtractor graphics, List<KnownItem> shown, String query, int firstRow) {
        return renderAt(graphics, shown, query, firstRow, NOWHERE, NOWHERE);
    }

    private static Drawn renderAt(GuiGraphicsExtractor graphics, List<KnownItem> shown, String query, int firstRow,
                                  int cursorX, int cursorY) {
        return KnownItemsOverlayRenderer.render(graphics, mock(Font.class),
                new KnownItemsOverlayRenderer.Frame(WIDTH, HEIGHT, noTarget(), shown, query, firstRow, cursorX,
                        cursorY));
    }

    /** A plexer with no target set: its column draws the Target label alone. */
    private static ItemStack noTarget() {
        ItemStack stack = mock(ItemStack.class);
        when(stack.isEmpty()).thenReturn(true);
        return stack;
    }

    /** The header the frame lays, with no target name. */
    private static HeaderLayout header() {
        return HeaderLayout.of(WIDTH, 0, 0);
    }

    @Nested
    class Frame {
        @Test
        void frameIsTheDarkeningTheTopRowOneIconPerItemTheHoveredCellAndTheHoveredName() {
            ItemIcon dirt = render(mock(GuiGraphicsExtractor.class), items, "", 0).icons().get(1);
            int cursorX = dirt.left() + 1;
            int cursorY = dirt.top() + 1;
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);
            Drawn drawn = renderAt(graphics, items, "", 0, cursorX, cursorY);
            KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn, cursorX, cursorY);

            // the darkening, the title, the search bar and its text, the Target label, the two rules, then the grid
            assertEquals(List.of("fill", "text", "fill", "fill", "text", "text", "fill", "fill", "item",
                    "fill", "fill", "fill", "fill", "fill", "item", "item",
                    "setTooltipForNextFrame"), methods(graphics));
            Invocation darken = calls(graphics).getFirst();
            assertEquals(List.of(0, 0, WIDTH, HEIGHT, KnownItemsOverlayRenderer.DARKEN), List.of(darken.getArguments()));
            List<Invocation> texts = callsOf(graphics, "text");
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.TITLE_KEY), texts.get(0).getArgument(1));
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.PLACEHOLDER_KEY), texts.get(1).getArgument(1));
            assertEquals(items.stream().map(KnownItem::stack).toList(),
                    callsOf(graphics, "item").stream().map(call -> call.getArgument(0)).toList());
            Invocation name = calls(graphics).getLast();
            assertEquals(List.of(font, Component.literal("Dirt"), cursorX, cursorY), List.of(name.getArguments()));
        }

        @Test
        void hoveredCellDrawsLighterInsideABorderRingJustBeforeItsItem() {
            ItemIcon dirt = render(mock(GuiGraphicsExtractor.class), items, "", 0).icons().get(1);
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            renderAt(graphics, items, "", 0, dirt.left() + 1, dirt.top() + 1);

            List<Invocation> fills = callsOf(graphics, "fill");
            List<int[]> ring = fills.stream()
                    .filter(fill -> (int) fill.getArgument(4) == KnownItemsOverlayRenderer.HOVER_BORDER)
                    .map(KnownItemsOverlayRendererTest::rect).toList();
            Invocation cell = fills.getLast();
            assertEquals(KnownItemsOverlayRenderer.HOVER_CELL, (int) cell.getArgument(4));
            int[] inside = rect(cell);
            int[] outer = {inside[0] - 1, inside[1] - 1, inside[2] + 1, inside[3] + 1};
            assertTrue(outer[0] <= dirt.left() && outer[2] >= dirt.left() + KnownItemsOverlayRenderer.ICON,
                    "the cell should hold the hovered icon");
            for (int x = outer[0]; x < outer[2]; x++) {
                for (int y = outer[1]; y < outer[3]; y++) {
                    boolean onEdge = x == outer[0] || y == outer[1] || x == outer[2] - 1 || y == outer[3] - 1;
                    int px = x;
                    int py = y;
                    long covering = ring.stream().filter(r -> px >= r[0] && px < r[2] && py >= r[1] && py < r[3])
                            .count();
                    assertEquals(onEdge ? 1 : 0, covering, "the border should cover the edge pixel " + x + "," + y
                            + " once and leave the inside to the lighter fill over the darkening");
                }
            }
        }

        /**
         * The title draws in its header box right of the rule, and the grid starts at the left margin under the
         * horizontal rule (decision menu-header-reserves-the-target).
         */
        @Test
        void titleSitsRightOfTheRuleAndTheGridStartsAtTheLeftMarginUnderTheHeader() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            List<ItemIcon> icons = render(graphics, items, "", 0).icons();
            Invocation title = callsOf(graphics, "text").getFirst();
            assertEquals(header().title().left(), (int) title.getArgument(2));
            int inset = (KnownItemsOverlayRenderer.CELL - KnownItemsOverlayRenderer.ICON) / 2;
            assertEquals(KnownItemsOverlayRenderer.MARGIN + KnownItemsOverlayRenderer.CELL + inset, icons.getFirst().left(),
                    "the first item should sit in the second cell, after Cancel");
            assertTrue(icons.getFirst().top() > header().horizontalRuleY(), "the grid should start under the header");
            assertEquals(icons.getFirst().top(), icons.getLast().top(), "a short list should fill its first row left to right");
        }

        /** The search bar draws in its header box, beneath the title, inside a one-pixel border. */
        @Test
        void searchBarDrawsInItsHeaderBox() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            render(graphics, items, "", 0);
            Invocation searchBorder = callsOf(graphics, "fill").get(1);
            HeaderLayout.Area search = header().search();
            assertEquals(List.of(search.left() - 1, search.top() - 1, search.right() + 1, search.bottom() + 1),
                    List.of(rect(searchBorder)[0], rect(searchBorder)[1], rect(searchBorder)[2], rect(searchBorder)[3]));
        }

        /** A one-pixel vertical rule parts the columns and a one-pixel horizontal rule runs under them. */
        @Test
        void rulesDrawWhereTheHeaderLaysThem() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            render(graphics, items, "", 0);
            List<Invocation> fills = callsOf(graphics, "fill");
            int[] vertical = rect(fills.get(3));
            int[] horizontal = rect(fills.get(4));
            assertEquals(header().verticalRuleX(), vertical[0]);
            assertEquals(1, vertical[2] - vertical[0]);
            assertEquals(header().horizontalRuleY(), horizontal[1]);
            assertEquals(1, horizontal[3] - horizontal[1]);
            assertEquals(List.of(KnownItemsOverlayRenderer.SEARCH_BORDER, KnownItemsOverlayRenderer.SEARCH_BORDER),
                    List.of(fills.get(3).getArgument(4), fills.get(4).getArgument(4)));
        }

        /** With no target set, the column draws the Target label and no item. */
        @Test
        void emptyTargetDrawsTheLabelAlone() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            render(graphics, List.of(), "", 0);
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.TARGET_KEY),
                    callsOf(graphics, "text").get(2).getArgument(1));
            assertTrue(callsOf(graphics, "item").isEmpty());
        }

        @Test
        void closeButtonSitsInTheTopRightCorner() {
            KnownItemsOverlayRenderer.Box close = KnownItemsOverlayRenderer.closeButton(WIDTH);
            assertEquals(WIDTH - KnownItemsOverlayRenderer.MARGIN, close.left() + close.size());
            assertEquals(KnownItemsOverlayRenderer.MARGIN, close.top());
        }
    }

    @Nested
    class Search {
        @Test
        void searchBarAndTypedTextDrawAboveTheGridAndOnlyTheMatchesDraw() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            List<KnownItem> matches = KnownItemsListing.filter(items, "s", Locale.ENGLISH);
            List<ItemIcon> icons = render(graphics, matches, "s", 0).icons();

            int gridTop = icons.stream().mapToInt(ItemIcon::top).min().orElseThrow();
            Invocation border = callsOf(graphics, "fill").get(1);
            Invocation bar = callsOf(graphics, "fill").get(2);
            assertEquals(KnownItemsOverlayRenderer.SEARCH_BORDER, (int) border.getArgument(4));
            assertEquals(KnownItemsOverlayRenderer.SEARCH_BAR, (int) bar.getArgument(4));
            assertEquals(List.of((int) border.getArgument(0) + 1, (int) border.getArgument(1) + 1,
                    (int) border.getArgument(2) - 1, (int) border.getArgument(3) - 1),
                    List.of(bar.getArgument(0), bar.getArgument(1), bar.getArgument(2), bar.getArgument(3)),
                    "the border should frame the bar one pixel wide");
            assertTrue((int) border.getArgument(3) <= gridTop, "the bar should end above the grid");
            Invocation text = callsOf(graphics, "text").get(1);
            assertEquals("s" + KnownItemsOverlayRenderer.CARET, text.getArgument(1));
            assertTrue((int) text.getArgument(3) < gridTop, "the typed text should draw above the grid");
            assertEquals(List.of(matches.get(0).stack(), matches.get(1).stack()),
                    callsOf(graphics, "item").stream().map(call -> call.getArgument(0)).toList());
        }
    }

    @Nested
    class Scroll {
        @Test
        void longListDrawsATrackAndAThumbAtTheGridsRightEdge() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Drawn drawn = render(graphics, longList, "", 0);

            assertEquals(List.of(KnownItemsOverlayRenderer.DARKEN, KnownItemsOverlayRenderer.SEARCH_BORDER,
                    KnownItemsOverlayRenderer.SEARCH_BAR, KnownItemsOverlayRenderer.SEARCH_BORDER,
                    KnownItemsOverlayRenderer.SEARCH_BORDER, KnownItemsOverlayRenderer.TRACK,
                    KnownItemsOverlayRenderer.THUMB), fillColors(graphics));
            assertNotNull(drawn.track());
            int gridRight = drawn.icons().stream().mapToInt(icon -> icon.left() + KnownItemsOverlayRenderer.ICON)
                    .max().orElseThrow();
            assertTrue(drawn.track().left() >= gridRight, "the track should stand right of the grid");
            assertTrue(drawn.scroll().scrolls(), "a long list should scroll");
        }

        @Test
        void shortListDrawsNoTrack() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Drawn drawn = render(graphics, items, "", 0);
            assertNull(drawn.track());
            assertEquals(List.of(KnownItemsOverlayRenderer.DARKEN, KnownItemsOverlayRenderer.SEARCH_BORDER,
                    KnownItemsOverlayRenderer.SEARCH_BAR, KnownItemsOverlayRenderer.SEARCH_BORDER,
                    KnownItemsOverlayRenderer.SEARCH_BORDER), fillColors(graphics));
        }

        @Test
        void oneStepDownDrawsTheSecondRowFirst() {
            Drawn top = render(mock(GuiGraphicsExtractor.class), longList, "", 0);
            // Cancel takes the first row's first cell, so that row shows one item fewer than the columns.
            int columns = 1 + (int) top.icons().stream().filter(icon -> icon.top() == top.icons().getFirst().top())
                    .count();
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            render(graphics, longList, "", top.scroll().scrolledBy(1).firstRow());
            assertSame(longList.get(columns - 1).stack(), callsOf(graphics, "item").getFirst().getArgument(0));
        }
    }

    @Nested
    class Click {
        private final Drawn drawn = render(mock(GuiGraphicsExtractor.class), items, "", 0);

        @Test
        void clickOnAnIconPicksThatItem() {
            ItemIcon sand = drawn.icons().get(2);
            assertEquals(new KnownItemsOverlayRenderer.Pick(Optional.of(SAND)),
                    KnownItemsOverlayRenderer.pickAt(drawn, sand.left() + 1, sand.top() + 1));
        }

        @Test
        void clickOnNoCellPicksNothing() {
            assertNull(KnownItemsOverlayRenderer.pickAt(drawn, 0, 0));
        }

        @Test
        void clickOnCancelPicksTheClear() {
            KnownItemsOverlayRenderer.Box cancel = drawn.cancel();
            assertNotNull(cancel);
            assertEquals(KnownItemsOverlayRenderer.Pick.CLEAR,
                    KnownItemsOverlayRenderer.pickAt(drawn, cancel.left() + 1, cancel.top() + 1));
        }
    }

    @Nested
    class Cancel {
        private void assertCancelHeadsTheGrid(List<KnownItem> shown, String query) {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Drawn drawn = render(graphics, shown, query, 0);
            KnownItemsOverlayRenderer.Box cancel = drawn.cancel();
            assertNotNull(cancel, "Cancel should draw for query \"" + query + "\"");
            assertEquals(KnownItemsOverlayRenderer.MARGIN, cancel.left(), "Cancel should take the first column");
            assertTrue(drawn.icons().stream().allMatch(icon -> icon.top() > cancel.top()
                    || icon.left() > cancel.left() + KnownItemsOverlayRenderer.CELL - 1),
                    "every item should follow Cancel");
            List<Invocation> glyph = calls(graphics).stream().filter(KnownItemsOverlayRendererTest::cancelGlyph).toList();
            assertFalse(glyph.isEmpty(), "the Cancel glyph should draw");
            assertTrue(glyph.stream().map(KnownItemsOverlayRendererTest::rect).allMatch(r -> r[0] >= cancel.left()
                    && r[2] <= cancel.left() + cancel.size() && r[1] >= cancel.top()
                    && r[3] <= cancel.top() + cancel.size()), "the glyph should draw inside the Cancel cell");
        }

        @Test
        void cancelHeadsTheGridWithAnEmptyQuery() {
            assertCancelHeadsTheGrid(items, "");
        }

        @Test
        void cancelHeadsTheGridWhenTheQueryMatchesNoItem() {
            assertCancelHeadsTheGrid(KnownItemsListing.filter(items, "zzz", Locale.ENGLISH), "zzz");
        }

        @Test
        void cursorOnCancelDrawsItsCellHoveredAndNamesIt() {
            KnownItemsOverlayRenderer.Box cancel = render(mock(GuiGraphicsExtractor.class), items, "", 0).cancel();
            assertNotNull(cancel);
            int cursorX = cancel.left() + 1;
            int cursorY = cancel.top() + 1;
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);
            Drawn drawn = renderAt(graphics, items, "", 0, cursorX, cursorY);
            KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, drawn, cursorX, cursorY);

            assertTrue(callsOf(graphics, "fill").stream()
                    .anyMatch(fill -> (int) fill.getArgument(4) == KnownItemsOverlayRenderer.HOVER_CELL
                            && (int) fill.getArgument(0) == cancel.left() + 1), "Cancel's cell should draw hovered");
            Invocation name = calls(graphics).getLast();
            assertEquals(List.of(font, Component.translatable(KnownItemsOverlayRenderer.CANCEL_KEY), cursorX, cursorY),
                    List.of(name.getArguments()));
        }

        @Test
        void glyphIsARingSlashedFromTopLeftToBottomRight() {
            assertTrue(KnownItemsOverlayRenderer.cancelPixel(6.5, 0), "the ring");
            assertTrue(KnownItemsOverlayRenderer.cancelPixel(0, -6.5), "the ring");
            assertTrue(KnownItemsOverlayRenderer.cancelPixel(-3, -3), "the slash's top left");
            assertTrue(KnownItemsOverlayRenderer.cancelPixel(3, 3), "the slash's bottom right");
            assertFalse(KnownItemsOverlayRenderer.cancelPixel(3, -3), "inside the ring, off the slash");
            assertFalse(KnownItemsOverlayRenderer.cancelPixel(-3, 3), "inside the ring, off the slash");
            assertFalse(KnownItemsOverlayRenderer.cancelPixel(7.9, 0), "outside the ring");
        }
    }
}
