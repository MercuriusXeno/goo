package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests the link and hover every item icon in a used-by list, an indirectly list or a group's members takes, with
 * no Screen (decision used-by-icons-link-to-items-and-hover-their-value).
 */
class ItemLinksTest {
    private static final String STICK = "minecraft:stick";

    /**
     * A click on the icon opens the Items tab on its item, and hovering it names the item.
     */
    @Test
    void iconClickOpensItsItem() {
        ManagerActions actions = mock(ManagerActions.class);
        ClickTargets targets = new ClickTargets();
        ItemLinks.add(targets, actions, STICK, 20, 30);
        assertEquals(ItemIcons.hoverKey(STICK), targets.hovered(25, 35).orElseThrow());
        assertTrue(targets.click(25, 35));
        verify(actions).openItem(STICK);
    }

    /**
     * A valued item's tooltip reads its name then one line per goo type with its amount; a valueless item reads its
     * name alone.
     */
    @Test
    void tooltipReadsNameThenEachTypesAmount() {
        List<Component> valued = ItemIcons.tooltipLines("Stick", Map.of("leaf", "48"));
        assertEquals(2, valued.size());
        assertEquals("Stick", valued.getFirst().getString());
        assertTrue(valued.get(1).getString().endsWith("48"));
        List<Component> valueless = ItemIcons.tooltipLines("Torch", Map.of());
        assertEquals(List.of("Torch"), valueless.stream().map(Component::getString).toList());
    }
}
