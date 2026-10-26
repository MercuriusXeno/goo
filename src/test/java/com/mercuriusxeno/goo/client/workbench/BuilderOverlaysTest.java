package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests where each popup the builders open stands, under the line of the control that opened it
 * (decision popups-anchor-under-the-line-that-opened-them).
 */
class BuilderOverlaysTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final int ANCHOR_X = 40;

    private static final Map<String, Function<BuilderOverlays.Anchor, BuilderOverlays.Placement>> POPUPS = Map.of(
            "picker", at -> BuilderOverlays.placePicker(at, SCREEN_WIDTH, SCREEN_HEIGHT),
            "dropdown", at -> BuilderOverlays.placeDropdown(at, SCREEN_WIDTH, SCREEN_HEIGHT),
            "number", at -> BuilderOverlays.placeNumber(at, SCREEN_WIDTH, SCREEN_HEIGHT));

    @Nested
    class WithRoomBelow {
        /**
         * Each popup's top sits on the line below its control and its left on the control's left edge.
         */
        @Test
        void everyPopupStandsUnderItsControl() {
            BuilderOverlays.Anchor at = new BuilderOverlays.Anchor(ANCHOR_X, 30);
            POPUPS.forEach((popup, place) -> {
                BuilderOverlays.Placement placed = place.apply(at);
                assertEquals(at.y() + BuilderOverlays.LINE, placed.top(), popup);
                assertEquals(at.x(), placed.left(), popup);
            });
        }
    }

    @Nested
    class NearTheBottom {
        /**
         * A popup whose line sits within its height of the screen bottom stays under the line, its height clamped so
         * its bottom stays on the screen.
         */
        @Test
        void everyPopupStaysUnderItsLineAndClampsItsHeight() {
            BuilderOverlays.Anchor at = new BuilderOverlays.Anchor(ANCHOR_X, SCREEN_HEIGHT - BuilderOverlays.LINE - 10);
            POPUPS.forEach((popup, place) -> {
                BuilderOverlays.Placement placed = place.apply(at);
                assertEquals(at.y() + BuilderOverlays.LINE, placed.top(), popup);
                assertTrue(placed.top() + placed.height() <= SCREEN_HEIGHT, popup);
            });
        }
    }
}
