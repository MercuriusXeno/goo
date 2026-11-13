package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * An item, a $prefab and a #group a finding names each hover and follow on shift + left click as they do on every
 * other tab (decision audit-findings-link-and-hover-like-every-other-tab).
 */
class FindingLinksTest {
    private static final String LINE = "minecraft:iron_ingot undercuts $wax in #planks.";

    @Test
    void aWordNamesAnItemByItsIdAPrefabAfterADollarAndAGroupAfterAHashLeavingThePunctuationOut() {
        assertEquals(List.of(
                        Optional.of(new FindingLinks.Link(FindingLinks.Kind.ITEM, "minecraft:iron_ingot", 0,
                                "minecraft:iron_ingot")),
                        Optional.of(new FindingLinks.Link(FindingLinks.Kind.PREFAB, "wax", 1, "$wax")),
                        Optional.of(new FindingLinks.Link(FindingLinks.Kind.GROUP, "planks", 0, "#planks"))),
                List.of(FindingLinks.linkOf("minecraft:iron_ingot"), FindingLinks.linkOf("($wax),"),
                        FindingLinks.linkOf("#planks.")));
    }

    @Test
    void eachNameInALineHoversItsKeyAndFollowsOnShiftClick() {
        DrawnFrame frame = new DrawnFrame();
        ManagerActions actions = mock(ManagerActions.class);

        FindingLinks.lay(frame.graphics, DrawnFrame.font(), LINE, 0, 0, frame.targets, actions);

        assertLinked(frame, "minecraft:iron_ingot", ItemIcons.hoverKey("minecraft:iron_ingot"));
        verify(actions).openItem("minecraft:iron_ingot");
        assertLinked(frame, "$wax", PrefabHovers.hoverKey("wax"));
        verify(actions).openVariable("wax");
        assertLinked(frame, "#planks", GroupHovers.hoverKey("planks"));
        verify(actions).openGroup("planks");
    }

    private static void assertLinked(DrawnFrame frame, String label, String hoverKey) {
        int x = DrawnFrame.font().width(LINE.substring(0, LINE.indexOf(label))) + 1;
        assertEquals(Optional.of(hoverKey), frame.targets.hoveredArea(x, 1)
                .map(ClickTargets.HoveredArea::hoverKey), label);
        assertTrue(frame.targets.click(x, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT), label);
    }
}
