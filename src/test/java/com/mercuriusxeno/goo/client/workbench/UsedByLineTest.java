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
 * Tests how many used-by items one line draws before it closes with its ..+n button, with no Screen
 * (decisions used-by-draws-the-terminus-items-alone, more-button-reads-dot-dot-plus-n).
 */
class UsedByLineTest {

    private static final int STEP = 18;

    /**
     * Items that all fit draw whole, with no "more" link.
     */
    @Test
    void itemsThatFitDrawWhole() {
        assertEquals(new UsedByLine.Fit(4, 0), UsedByLine.fit(4, 100, STEP, more -> 50));
    }

    /**
     * Items past the line's width stop short of room for the "more" link, which counts the rest.
     */
    @Test
    void itemsPastTheWidthStopBeforeTheMoreLink() {
        UsedByLine.Fit fit = UsedByLine.fit(20, 100, STEP, more -> 50);
        assertEquals(new UsedByLine.Fit(2, 18), fit);
        assertEquals("..+18", UsedByLine.moreLabel(fit.hidden()));
        assertEquals("..+3", UsedByLine.moreLabel(3));
    }

    /**
     * A link chip counts as a slot of its own width, so a line of icons and wider chips fits by the sum of its
     * slots and stops short of room for the "more" link (decision
     * used-by-lists-direct-items-and-links-to-prefabs-and-groups).
     */
    @Test
    void slotsOfTheirOwnWidthFitByTheirSum() {
        assertEquals(new UsedByLine.Fit(2, 0), UsedByLine.fitSlots(List.of(16, 40), 2, 100, more -> 20));
        assertEquals(new UsedByLine.Fit(3, 1), UsedByLine.fitSlots(List.of(16, 16, 40, 40), 2, 100,
                more -> 20));
    }

    /**
     * Slots flow left to right and wrap to a new row where the next one would pass the width; the end button
     * follows the last slot, on its row where it fits.
     */
    @Test
    void slotsFlowAndWrapWithTheEndButtonAfterTheLast() {
        assertEquals(List.of(new UsedByLine.Slot(0, 0), new UsedByLine.Slot(18, 0),
                new UsedByLine.Slot(0, 1), new UsedByLine.Slot(42, 1)),
                UsedByLine.flow(List.of(16, 40, 40), 2, 70, 20));
    }

    /**
     * An expanded line draws every item, wrapping as it must.
     */
    @Test
    void expandedLineDrawsEveryItem() {
        assertEquals(new UsedByLine.Fit(20, 0), UsedByLine.expanded(20));
    }

    /**
     * An expanded list ends on Hide, a collapsed one on its hidden count, and one that fits on nothing
     * (decision expanded-used-by-lists-take-a-hide-button).
     */
    @Test
    void expandedListEndsOnHideAndCollapsedOnItsCount() {
        assertEquals(Optional.of("Hide"), UsedByLine.endLabel(UsedByLine.expanded(20), true));
        assertEquals(Optional.of("..+3"), UsedByLine.endLabel(new UsedByLine.Fit(2, 3), false));
        assertEquals(Optional.empty(), UsedByLine.endLabel(new UsedByLine.Fit(4, 0), false));
    }

    /**
     * Hide stands after the last row's last item, or opens a row of its own where that row has no room for it.
     */
    @Test
    void hideStandsAfterTheLastItem() {
        assertEquals(new UsedByLine.Slot(2 * STEP, 1), UsedByLine.hideSlot(7, 5, STEP, 100, 30));
        assertEquals(new UsedByLine.Slot(0, 2), UsedByLine.hideSlot(10, 5, STEP, 100, 30));
    }

    /**
     * Each used-by user draws through the one draw the head's line and the delete dialog share: an item its linked
     * icon, a prefab its $chip and a group its #chip, each hovering its value and following on shift + left click
     * (decision one-used-by-primitive-draws-the-row-line-and-the-dialog).
     */
    @Test
    void eachUserDrawsItsChipOrIconWithItsHoverAndFollow() {
        ManagerActions actions = mock(ManagerActions.class);
        DrawnFrame frame = new DrawnFrame();
        UsedByLine.Painter painter = new UsedByLine.Painter(frame.graphics, DrawnFrame.font(), mock(ItemIcons.class),
                actions, frame.targets);
        int prefabWidth = UsedByLine.drawUser(painter, new VariablesTab.Reader("egg", UsedByLine.PREFAB_USER), 0, 0);
        int groupWidth = UsedByLine.drawUser(painter, new VariablesTab.Reader("logs", UsedByLine.GROUP_USER), 100, 0);
        UsedByLine.drawUser(painter, new VariablesTab.Reader("minecraft:stick", "item"), 200, 0);
        assertEquals(ButtonPainter.width(DrawnFrame.font(), "$egg"), prefabWidth);
        assertEquals(ButtonPainter.width(DrawnFrame.font(), "#logs"), groupWidth);
        assertHoverAndFollow(frame, frame.find("$egg").x() + 1, frame.find("$egg").y() + 1,
                PrefabHovers.hoverKey("egg"));
        verify(actions).openVariable("egg");
        assertHoverAndFollow(frame, frame.find("#logs").x() + 1, frame.find("#logs").y() + 1,
                GroupHovers.hoverKey("logs"));
        verify(actions).openGroup("logs");
        assertHoverAndFollow(frame, 201, 1, ItemIcons.hoverKey("minecraft:stick"));
        verify(actions).openItem("minecraft:stick");
    }

    /**
     * A prefab user's $chip underlines while shift is held (decision holding-shift-underlines-every-link).
     */
    @Test
    void aPrefabUsersChipUnderlinesWhileShiftIsHeld() {
        DrawnFrame frame = new DrawnFrame().holdingShift();
        UsedByLine.drawUser(new UsedByLine.Painter(frame.graphics, DrawnFrame.font(), mock(ItemIcons.class),
                mock(ManagerActions.class), frame.targets), new VariablesTab.Reader("egg", UsedByLine.PREFAB_USER), 0,
                0);
        assertTrue(frame.underlined("$egg"));
    }

    /**
     * A group user spelled with its # draws that one # and opens the group it names on shift + left click.
     */
    @Test
    void aGroupUserSpelledWithItsHashOpensTheGroupItNames() {
        ManagerActions actions = mock(ManagerActions.class);
        DrawnFrame frame = new DrawnFrame();
        UsedByLine.drawUser(new UsedByLine.Painter(frame.graphics, DrawnFrame.font(), mock(ItemIcons.class), actions,
                frame.targets), new VariablesTab.Reader("#logs", UsedByLine.GROUP_USER), 0, 0);
        assertHoverAndFollow(frame, frame.find("#logs").x() + 1, frame.find("#logs").y() + 1,
                GroupHovers.hoverKey("logs"));
        verify(actions).openGroup("logs");
    }

    private static void assertHoverAndFollow(DrawnFrame frame, int x, int y, String hoverKey) {
        assertEquals(Optional.of(hoverKey), frame.targets.hoveredArea(x, y).map(ClickTargets.HoveredArea::hoverKey));
        assertTrue(frame.targets.click(x, y, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT), hoverKey);
    }
}
