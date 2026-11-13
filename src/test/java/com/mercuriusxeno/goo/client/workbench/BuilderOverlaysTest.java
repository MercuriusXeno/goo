package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import org.junit.jupiter.api.Nested;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests where each popup the builders open stands, under the line of the control that opened it
 * (decision popups-anchor-under-the-line-that-opened-them).
 */
class BuilderOverlaysTest {
    private static final int SCREEN_WIDTH = 480;
    private static final int SCREEN_HEIGHT = 270;
    private static final int ANCHOR_X = 40;

    private static final int OPTIONS_WIDTH = 60;
    private static final int FEW_OPTIONS = 5;

    private static final Map<String, Function<BuilderOverlays.Anchor, BuilderOverlays.Placement>> POPUPS = Map.of(
            "picker", at -> BuilderOverlays.placePicker(at, SCREEN_WIDTH, SCREEN_HEIGHT),
            "dropdown", at -> BuilderOverlays.placeDropdown(at, SearchDropdown.SHOWN, SCREEN_WIDTH, SCREEN_HEIGHT),
            "options", at -> BuilderOverlays.placeOptions(at, OPTIONS_WIDTH, FEW_OPTIONS, SCREEN_WIDTH,
                    SCREEN_HEIGHT),
            "number", at -> BuilderOverlays.placeNumber(at, SCREEN_WIDTH, SCREEN_HEIGHT));

    // Overlays standing open in a shown builder, each widget they add handed back and kept.
    private static BuilderOverlays overlays(List<AbstractWidget> widgets) {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> {
            widgets.add(call.getArgument(0));
            return call.getArgument(0);
        });
        BuilderOverlays overlays = new BuilderOverlays(actions, mock(ItemIcons.class));
        overlays.init(DrawnFrame.font());
        overlays.show(true);
        return overlays;
    }

    private static List<BuilderOverlays.Option> options(int count) {
        return IntStream.range(0, count).mapToObj(index -> new BuilderOverlays.Option("option " + index, null,
                at -> { })).toList();
    }

    // The bottom edge of the popup frame drawn, its border included.
    private static int popupBottom(DrawnFrame frame) {
        return frame.fillsOf(Palette.POPUP_BORDER).stream().mapToInt(fill -> fill[3]).max()
                .orElseThrow(() -> new AssertionError("no popup frame drew"));
    }

    private static List<String> optionLabels(DrawnFrame frame) {
        return frame.texts().stream().filter(text -> text.startsWith("option ")).toList();
    }

    /**
     * The open option list frames itself in the popup role (decision one-frame-primitive-draws-every-bordered-box).
     */
    @Test
    void optionListFramesInThePopupRole() {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
        BuilderOverlays overlays = new BuilderOverlays(actions, null);
        overlays.init(DrawnFrame.font());
        overlays.show(true);
        overlays.openOptions(new BuilderOverlays.Anchor(20, 20), List.of(
                new BuilderOverlays.Option("relative", null, at -> { }),
                new BuilderOverlays.Option("absolute", null, at -> { })));
        DrawnFrame frame = new DrawnFrame();
        overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
        frame.assertOneFrameIn(Frame.POPUP);
    }

    /**
     * Escape over an open dropdown closes the dropdown (decision escape-empties-the-box-and-never-closes-the-manager).
     */
    @Test
    void escapeClosesAnOpenDropdown() {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
        BuilderOverlays overlays = new BuilderOverlays(actions, null);
        overlays.init(DrawnFrame.font());
        overlays.show(true);
        overlays.openDropdown(new BuilderOverlays.Anchor(20, 20), SearchDropdown.over(List.of(), List.of("oak"),
                false), choice -> { });
        assertTrue(overlays.listOpen());
        assertTrue(overlays.keyPressed(GLFW.GLFW_KEY_ESCAPE));
        assertFalse(overlays.listOpen());
    }

    /**
     * A click outside an open options list closes the list (decision exclusive-choice-sets-draw-as-dropdowns).
     */
    @Test
    void aClickOutsideAnOpenOptionsListClosesIt() {
        BuilderOverlays overlays = new BuilderOverlays(mock(ManagerActions.class), null);
        overlays.openOptions(new BuilderOverlays.Anchor(20, 20), List.of(
                new BuilderOverlays.Option("north", null, at -> { }),
                new BuilderOverlays.Option("south", null, at -> { })));
        DrawnFrame open = new DrawnFrame();
        overlays.draw(open.graphics, DrawnFrame.font(), open.targets);

        assertTrue(open.targets.click(DrawnFrame.SCREEN_WIDTH - 1, DrawnFrame.SCREEN_HEIGHT - 1), "the click took");
        assertFalse(overlays.listOpen());
    }

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
         * A popup whose line sits too near the screen bottom to hold it below opens above the line instead, its
         * bottom on the line's top and its whole height on the screen: the operator found beetroot soup's item list
         * opened off the screen's bottom, unusable.
         */
        @Test
        void everyPopupShortOfRoomBelowOpensAboveItsLine() {
            BuilderOverlays.Anchor at = new BuilderOverlays.Anchor(ANCHOR_X, SCREEN_HEIGHT - BuilderOverlays.LINE - 10);
            BuilderOverlays.Anchor roomy = new BuilderOverlays.Anchor(ANCHOR_X, 0);
            POPUPS.forEach((popup, place) -> {
                BuilderOverlays.Placement placed = place.apply(at);
                int full = place.apply(roomy).height();
                assertEquals(List.of(at.y() - full, full), List.of(placed.top(), placed.height()),
                        popup + " stands above its line at its full height");
            });
        }

        /**
         * The picker, the searchable dropdown, the options list and the number box, opened at one anchor whose room
         * below falls short of each and whose room above holds each, all stand with their bottom on the anchor's line
         * top, the number box from the moment it opens (decision every-popup-measures-flows-to-fit-and-scrolls).
         */
        @Test
        void everyPopupDrawnShortOfRoomBelowStandsOnItsLineTop() {
            BuilderOverlays.Anchor at = new BuilderOverlays.Anchor(ANCHOR_X,
                    DrawnFrame.SCREEN_HEIGHT - BuilderOverlays.LINE - 10);
            Map<String, Consumer<BuilderOverlays>> opens = Map.of(
                    "picker", overlays -> overlays.openPicker(at, type -> { }),
                    "dropdown", overlays -> overlays.openDropdown(at, SearchDropdown.over(List.of(),
                            List.of("oak"), false), choice -> { }),
                    "options", overlays -> overlays.openOptions(at, options(FEW_OPTIONS)));
            Map<String, Integer> bottoms = new java.util.TreeMap<>();
            opens.forEach((popup, open) -> {
                BuilderOverlays overlays = overlays(new ArrayList<>());
                open.accept(overlays);
                DrawnFrame frame = new DrawnFrame();
                overlays.draw(frame.graphics, DrawnFrame.font(), frame.targets);
                bottoms.put(popup, popupBottom(frame));
            });
            List<AbstractWidget> widgets = new ArrayList<>();
            BuilderOverlays overlays = overlays(widgets);
            overlays.draw(new DrawnFrame().graphics, DrawnFrame.font(), new DrawnFrame().targets);
            overlays.openNumber(at, "3", typed -> { });
            AbstractWidget box = widgets.getFirst();
            bottoms.put("number as it opens", box.getY() + box.getHeight());
            overlays.draw(new DrawnFrame().graphics, DrawnFrame.font(), new DrawnFrame().targets);
            bottoms.put("number once drawn", box.getY() + box.getHeight());
            Map<String, Integer> expected = new java.util.TreeMap<>();
            bottoms.keySet().forEach(popup -> expected.put(popup, at.y()));
            assertEquals(expected, bottoms, "each popup's bottom on the line top at " + at.y());
        }
    }

    @Nested
    class ShortOfRoomOnBothSides {
        /**
         * A popup fitting whole on neither side stands on the side with more room, at the height that side holds
         * (decision every-popup-measures-flows-to-fit-and-scrolls).
         */
        @Test
        void popupFittingNeitherSideTakesTheSideWithMoreRoom() {
            int tall = SCREEN_HEIGHT;
            BuilderOverlays.Anchor low = new BuilderOverlays.Anchor(ANCHOR_X, 200);
            BuilderOverlays.Placement above = BuilderOverlays.place(low, OPTIONS_WIDTH, tall, SCREEN_WIDTH,
                    SCREEN_HEIGHT);
            assertEquals(List.of(0, low.y()), List.of(above.top(), above.height()), "more room above");
            BuilderOverlays.Anchor high = new BuilderOverlays.Anchor(ANCHOR_X, 40);
            BuilderOverlays.Placement below = BuilderOverlays.place(high, OPTIONS_WIDTH, tall, SCREEN_WIDTH,
                    SCREEN_HEIGHT);
            int roomBelow = SCREEN_HEIGHT - high.y() - BuilderOverlays.LINE;
            assertEquals(List.of(high.y() + BuilderOverlays.LINE, roomBelow), List.of(below.top(), below.height()),
                    "more room below");
        }

        /**
         * An options list longer than either side holds draws the rows its placement holds beside a scroll bar, and a
         * wheel notch scrolls the rows drawn by one (decision every-popup-measures-flows-to-fit-and-scrolls).
         */
        @Test
        void optionsListLongerThanTheScreenScrolls() {
            int count = 60;
            BuilderOverlays overlays = overlays(new ArrayList<>());
            BuilderOverlays.Anchor at = new BuilderOverlays.Anchor(ANCHOR_X, DrawnFrame.SCREEN_HEIGHT - 40);
            overlays.openOptions(at, options(count));
            DrawnFrame first = new DrawnFrame();
            overlays.draw(first.graphics, DrawnFrame.font(), first.targets);
            List<String> shown = optionLabels(first);
            int rows = shown.size();
            assertTrue(rows > 0 && rows < count, rows + " rows drew of " + count);
            assertEquals(options(rows).stream().map(BuilderOverlays.Option::label).toList(), shown);
            assertTrue(popupBottom(first) <= at.y(), "the list stands within the room above its line");
            assertFalse(first.fillsOf(Palette.SCROLL_TRACK).isEmpty(), "a scroll bar draws beside the rows");
            assertTrue(overlays.scrollList(-1), "the wheel reaches the open options list");
            DrawnFrame scrolled = new DrawnFrame();
            overlays.draw(scrolled.graphics, DrawnFrame.font(), scrolled.targets);
            assertEquals(options(rows + 1).stream().skip(1).map(BuilderOverlays.Option::label).toList(),
                    optionLabels(scrolled));
        }
    }

    /**
     * The number box opens with its whole text selected, so the first typed character replaces it (decision
     * every-number-box-opens-with-its-text-selected).
     */
    @Test
    void numberBoxOpensWithItsTextSelected() {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any(AbstractWidget.class))).thenAnswer(call -> call.getArgument(0));
        List<EditBox> boxes = new ArrayList<>();
        doAnswer(call -> boxes.add(call.getArgument(0))).when(actions).focus(any(AbstractWidget.class));
        Font font = mock(Font.class);
        when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
        when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        BuilderOverlays overlays = new BuilderOverlays(actions, mock(ItemIcons.class));
        overlays.init(font);
        overlays.show(true);
        overlays.openNumber(new BuilderOverlays.Anchor(ANCHOR_X, 30), "12", typed -> { });
        assertEquals("12", boxes.getFirst().getHighlighted());
    }
}
