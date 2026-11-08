package com.mercuriusxeno.goo.client.knownitems;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * The overlay's frame is one darkening fill over the whole screen, one icon per known item and the
 * hovered item's name beside the cursor, and nothing else; a click on an icon picks that item
 * (decisions menu-is-a-darkening-overlay-not-a-screen, menu-places-a-ghost-item-in-the-empty-hand).
 */
class KnownItemsOverlayRendererTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final Identifier STONE = Identifier.withDefaultNamespace("stone");
    private static final Identifier DIRT = Identifier.withDefaultNamespace("dirt");
    private static final Identifier SAND = Identifier.withDefaultNamespace("sand");

    private static KnownItem known(Identifier id, String name) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getHoverName()).thenReturn(Component.literal(name));
        return new KnownItem(id, stack);
    }

    private final List<KnownItem> items = List.of(known(STONE, "Stone"), known(DIRT, "Dirt"), known(SAND, "Sand"));

    private static List<Invocation> calls(GuiGraphicsExtractor graphics) {
        return List.copyOf(mockingDetails(graphics).getInvocations());
    }

    @Nested
    class Frame {
        @Test
        void frameIsOneDarkeningFillOneIconPerItemAndTheHoveredName() {
            GuiGraphicsExtractor graphics = mock(GuiGraphicsExtractor.class);
            Font font = mock(Font.class);
            List<ItemIcon> icons = KnownItemsOverlayRenderer.render(graphics,
                    new KnownItemsOverlayRenderer.Frame(WIDTH, HEIGHT, items));
            ItemIcon dirt = icons.get(1);
            int cursorX = dirt.left() + 1;
            int cursorY = dirt.top() + 1;
            KnownItemsOverlayRenderer.nameWhatTheCursorRestsOn(graphics, font, icons, cursorX, cursorY);

            List<Invocation> calls = calls(graphics);
            assertEquals(1 + items.size() + 1, calls.size(), "every draw: " + calls);
            Invocation fill = calls.getFirst();
            assertEquals("fill", fill.getMethod().getName());
            assertEquals(List.of(0, 0, WIDTH, HEIGHT, KnownItemsOverlayRenderer.DARKEN),
                    List.of(fill.getArguments()));
            List<Object> drawnStacks = calls.subList(1, 1 + items.size()).stream()
                    .peek(call -> assertEquals("item", call.getMethod().getName()))
                    .map(call -> call.getArgument(0))
                    .toList();
            assertEquals(items.stream().map(KnownItem::stack).toList(), drawnStacks);
            Invocation name = calls.getLast();
            assertEquals("setTooltipForNextFrame", name.getMethod().getName());
            assertEquals(List.of(font, Component.literal("Dirt"), cursorX, cursorY), List.of(name.getArguments()));
        }
    }

    @Nested
    class Click {
        private final List<ItemIcon> icons = KnownItemsOverlayRenderer.render(mock(GuiGraphicsExtractor.class),
                new KnownItemsOverlayRenderer.Frame(WIDTH, HEIGHT, items));

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
