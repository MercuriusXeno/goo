package com.mercuriusxeno.goo.client.knownitems;

import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.Drawn;
import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.ItemIcon;
import com.mercuriusxeno.goo.client.knownitems.KnownItemsOverlayRenderer.KnownItem;
import com.mercuriusxeno.goo.network.GhostItemPickPayload;
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
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * (decisions menu-is-a-darkening-overlay-not-a-screen, menu-places-a-ghost-item-in-the-empty-hand,
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

    private static List<String> methods(GuiGraphicsExtractor graphics) {
        return calls(graphics).stream().map(call -> call.getMethod().getName()).toList();
    }

    private static List<Invocation> callsOf(GuiGraphicsExtractor graphics, String method) {
        return calls(graphics).stream().filter(call -> call.getMethod().getName().equals(method)).toList();
    }

    private static Drawn render(GuiGraphicsExtractor graphics, List<KnownItem> shown, String query, int firstRow) {
        return KnownItemsOverlayRenderer.render(graphics, mock(Font.class),
                new KnownItemsOverlayRenderer.Frame(WIDTH, HEIGHT, shown, query, firstRow));
    }

    @Nested
    class Frame {
        @Test
        void frameIsTheDarkeningTheSearchBarOneIconPerItemAndTheHoveredName() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);
            List<ItemIcon> icons = render(graphics, items, "", 0).icons();
            ItemIcon dirt = icons.get(1);
            int cursorX = dirt.left() + 1;
            int cursorY = dirt.top() + 1;
            KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, icons, cursorX, cursorY);

            assertEquals(List.of("fill", "text", "fill", "fill", "text", "item", "item", "item", "text",
                    "setTooltipForNextFrame"), methods(graphics));
            Invocation darken = calls(graphics).getFirst();
            assertEquals(List.of(0, 0, WIDTH, HEIGHT, KnownItemsOverlayRenderer.DARKEN), List.of(darken.getArguments()));
            List<Invocation> texts = callsOf(graphics, "text");
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.TITLE_KEY), texts.get(0).getArgument(1));
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.PLACEHOLDER_KEY), texts.get(1).getArgument(1));
            assertEquals(Component.translatable(KnownItemsOverlayRenderer.CLOSE_HINT_KEY), texts.get(2).getArgument(1));
            assertEquals(items.stream().map(KnownItem::stack).toList(),
                    callsOf(graphics, "item").stream().map(call -> call.getArgument(0)).toList());
            Invocation name = calls(graphics).getLast();
            assertEquals(List.of(font, Component.literal("Dirt"), cursorX, cursorY), List.of(name.getArguments()));
        }

        @Test
        void titleDrawsAboveTheSearchBarAndTheHintUnderTheGrid() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            List<ItemIcon> icons = render(graphics, items, "", 0).icons();
            List<Invocation> texts = callsOf(graphics, "text");
            int barTop = callsOf(graphics, "fill").get(1).getArgument(1);
            int gridBottom = icons.stream().mapToInt(icon -> icon.top() + KnownItemsOverlayRenderer.ICON).max()
                    .orElseThrow();
            assertTrue((int) texts.get(0).getArgument(3) < barTop, "the title should draw above the search bar");
            assertTrue((int) texts.get(2).getArgument(3) >= gridBottom, "the hint should draw under the grid");
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

            List<Invocation> fills = callsOf(graphics, "fill");
            assertEquals(List.of(KnownItemsOverlayRenderer.DARKEN, KnownItemsOverlayRenderer.SEARCH_BORDER,
                    KnownItemsOverlayRenderer.SEARCH_BAR, KnownItemsOverlayRenderer.TRACK, KnownItemsOverlayRenderer.THUMB),
                    fills.stream().map(fill -> (Object) fill.getArgument(4)).toList());
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
                    KnownItemsOverlayRenderer.SEARCH_BAR),
                    callsOf(graphics, "fill").stream().map(fill -> (Object) fill.getArgument(4)).toList());
        }

        @Test
        void oneStepDownDrawsTheSecondRowFirst() {
            Drawn top = render(mock(GuiGraphicsExtractor.class), longList, "", 0);
            int columns = (int) top.icons().stream().filter(icon -> icon.top() == top.icons().getFirst().top()).count();
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            render(graphics, longList, "", top.scroll().scrolledBy(1).firstRow());
            assertSame(longList.get(columns).stack(), callsOf(graphics, "item").getFirst().getArgument(0));
        }
    }

    @Nested
    class Click {
        private final List<ItemIcon> icons = render(mock(GuiGraphicsExtractor.class), items, "", 0).icons();

        @Test
        void clickOnAnIconPicksThatItem() {
            ItemIcon sand = icons.get(2);
            GhostItemPickPayload pick = KnownItemsOverlayRenderer.pickAt(icons, sand.left() + 1, sand.top() + 1);
            assertNotNull(pick);
            assertEquals(SAND, pick.item());
        }

        @Test
        void clickOnNoIconPicksNothing() {
            assertNull(KnownItemsOverlayRenderer.pickAt(icons, 0, 0));
        }
    }
}
