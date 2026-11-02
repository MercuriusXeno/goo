package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests the link and hover every item icon in a used-by list, an indirectly list or a group's members takes, with
 * no Screen (decision used-by-icons-link-to-items-and-hover-their-value).
 */
class ItemLinksTest {
    private static final String STICK = "minecraft:stick";

    /**
     * A shift + left click on the icon opens the Items tab on its item, a plain left click runs nothing of the icon's
     * and reaches the area under it, and hovering it names the item (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void iconShiftClickOpensItsItemAndPlainClickPassesUnder() {
        ManagerActions actions = mock(ManagerActions.class);
        ClickTargets targets = new ClickTargets();
        List<String> beneath = new ArrayList<>();
        targets.add(0, 0, 100, 100, () -> beneath.add("beneath"));
        ItemLinks.add(targets, actions, STICK, 20, 30);
        assertEquals(ItemIcons.hoverKey(STICK), targets.hovered(25, 35).orElseThrow());
        assertTrue(targets.click(25, 35));
        verifyNoInteractions(actions);
        assertEquals(List.of("beneath"), beneath);
        assertTrue(targets.click(25, 35, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT));
        verify(actions).openItem(STICK);
        assertEquals(List.of("beneath"), beneath);
    }

    /**
     * A valued item's tooltip reads its name then one goo amount line per type, carrying the type's id and its
     * amount and no type name; a valueless item reads its name alone (decision
     * value-tooltips-draw-each-types-icon-before-its-amount).
     */
    @Test
    void tooltipReadsNameThenEachTypesAmount() {
        Map<String, String> amounts = new LinkedHashMap<>();
        amounts.put("leaf", "48");
        amounts.put("metal", "3/2");
        List<TooltipLine> valued = ItemIcons.tooltipLines("Stick", amounts);
        assertEquals(List.of(new TooltipLine.Text(Component.literal("Stick")),
                new TooltipLine.GooAmount("leaf", "48"), new TooltipLine.GooAmount("metal", "3/2")), valued);
        assertEquals(List.of(new TooltipLine.Text(Component.literal("Torch"))),
                ItemIcons.tooltipLines("Torch", Map.of()));
    }
}
