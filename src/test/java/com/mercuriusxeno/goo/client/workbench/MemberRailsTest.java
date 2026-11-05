package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests a held member rail's drop row against a detail column scrolled so the list's first row stands above its top
 * edge, with a mocked font six pixels a character wide (decision custom-group-members-drag-by-numbered-rails).
 */
class MemberRailsTest {

    /**
     * A rail held over a row the detail column shows only in part lights that part alone.
     */
    @Test
    void heldRailLightsOnlyTheVisiblePartOfItsDropRow() {
        List<int[]> moved = new ArrayList<>();
        MemberRails rails = new MemberRails((from, to) -> moved.add(new int[] {from, to}));
        GroupsPane.Bounds detail = new GroupsPane.Bounds(300, 100, 500, 300);
        int listTop = detail.top() - GroupsPane.CARD_HEIGHT / 2;
        rails.layList(listTop, 3);
        DrawnFrame frame = new DrawnFrame();
        GroupsPane.Bounds third = new GroupsPane.Bounds(detail.left(), listTop + 2 * GroupsPane.CARD_HEIGHT,
                detail.right(), listTop + 3 * GroupsPane.CARD_HEIGHT);
        rails.draw(frame.graphics, DrawnFrame.font(), third, 2, "minecraft:oak_log", frame.targets);
        assertTrue(frame.targets.click(third.left() + 1, third.top() + 1), "the rail takes the press");
        frame.targets.drag(third.left() + 1, listTop + 1);
        DrawnFrame held = new DrawnFrame();
        rails.drawHeld(held.graphics, detail);
        List<int[]> lit = DragMaskFills.of(held, Palette.DRAG_HIGHLIGHT);
        assertEquals(1, lit.size(), "one lit row");
        assertEquals(List.of(detail.left(), detail.top(), detail.right(), listTop + GroupsPane.CARD_HEIGHT),
                List.of(lit.getFirst()[0], lit.getFirst()[1], lit.getFirst()[2], lit.getFirst()[3]),
                "the first row lit from the column's top edge");
    }
}
