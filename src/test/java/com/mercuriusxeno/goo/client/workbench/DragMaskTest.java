package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the drag mask's geometry with no frame: its shadow covers every pixel of the screen once, save the pixels its
 * holes take, which it leaves bare (decision drags-mask-everything-but-their-valid-targets).
 */
class DragMaskTest {
    private static final DragMask.Rect SCREEN = new DragMask.Rect(0, 0, 60, 40);

    @Test
    void noHoleShadowsTheWholeScreen() {
        assertEquals(List.of(SCREEN), DragMask.shadow(SCREEN, List.of()));
    }

    @Test
    void shadowCoversTheScreenLessOverlappingHoles() {
        List<DragMask.Rect> holes = List.of(new DragMask.Rect(10, 5, 20, 15), new DragMask.Rect(15, 10, 30, 25),
                new DragMask.Rect(50, 30, 70, 50));
        assertCoversLessHoles(DragMask.shadow(SCREEN, holes), holes);
    }

    @Test
    void holesSharingAnEdgeLeaveNoShadowBetweenThem() {
        List<DragMask.Rect> holes = List.of(new DragMask.Rect(10, 10, 20, 20), new DragMask.Rect(20, 10, 30, 20));
        assertCoversLessHoles(DragMask.shadow(SCREEN, holes), holes);
    }

    // Every screen pixel lies in exactly one shadow rectangle, or in a hole and in none.
    private static void assertCoversLessHoles(List<DragMask.Rect> shadow, List<DragMask.Rect> holes) {
        for (int y = SCREEN.top(); y < SCREEN.bottom(); y++) {
            for (int x = SCREEN.left(); x < SCREEN.right(); x++) {
                int px = x;
                int py = y;
                long shaded = shadow.stream().filter(rect -> rect.holds(px, py)).count();
                boolean holed = holes.stream().anyMatch(rect -> rect.holds(px, py));
                assertEquals(holed ? 0 : 1, shaded, "pixel " + x + "," + y + " in " + shadow);
            }
        }
    }
}
