package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
 * Tests the values manager through its seams with no Screen: the title row's layout through its static seam, where
 * the screen's margin is 8, its button height 14 and its button gap 2, and the open's requests through
 * {@link ManagerReads} over real panes and a fake request line.
 */
class ValuesManagerScreenTest {
    private static final String MODEL = """
            {"path": "base_values.json", "members": [
              {"key": "stick", "keySeparator": null, "kind": "VALUE", "text": "\\"4\\"", "entries": [],
               "leading": null, "trailing": null, "closingGap": null}]}
            """;
    private static final String SAVED = MODEL.replace("\"text\": \"\\\"4\\\"\"", "\"text\": \"5\"");
    private static final Map<String, String> CANNED = Map.of(WorkbenchOperations.MODEL, MODEL);

    /**
     * The X is a square ending at the right margin, the [?] a square one gap left of it and the topbar starts one gap
     * left of the [?] (decisions x-in-the-top-right-closes-the-manager,
     * question-button-toggles-explainer-mode-with-the-drag-mask).
     */
    @Test
    void closeXEndsAtTheRightMarginAndTheTopbarStandsLeftOfIt() {
        ValuesManagerScreen.TitleRow row = ValuesManagerScreen.titleRowOf(400);
        assertEquals(new ValuesManagerScreen.TitleRow(378, 392, 362, 360), row);
    }

    /**
     * Explainer mode over five tabs laid 50 pixels apart on the tab row (decisions
     * question-button-toggles-explainer-mode-with-the-drag-mask, explainer-lines-are-the-operators-tab-descriptions).
     */
    @Nested
    class ExplainerModeOverTheTabs {
        private final ExplainerMode explainer = new ExplainerMode(ExplainerTargets.forTabs(tab ->
                new DragMask.Rect(8 + tab.ordinal() * 50, 20, 56 + tab.ordinal() * 50, 34)));

        private List<DragMask.Rect> fills(DrawnFrame frame, int color) {
            org.mockito.ArgumentCaptor<Integer> lefts = org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.ArgumentCaptor<Integer> tops = org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.ArgumentCaptor<Integer> rights = org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.ArgumentCaptor<Integer> bottoms = org.mockito.ArgumentCaptor.forClass(Integer.class);
            org.mockito.Mockito.verify(frame.graphics, org.mockito.Mockito.atLeast(0)).fill(lefts.capture(),
                    tops.capture(), rights.capture(), bottoms.capture(), org.mockito.ArgumentMatchers.eq(color));
            List<DragMask.Rect> rects = new ArrayList<>();
            for (int index = 0; index < lefts.getAllValues().size(); index++) {
                rects.add(new DragMask.Rect(lefts.getAllValues().get(index), tops.getAllValues().get(index),
                        rights.getAllValues().get(index), bottoms.getAllValues().get(index)));
            }
            return rects;
        }

        private DrawnFrame frame() {
            DrawnFrame frame = new DrawnFrame();
            explainer.extract(frame.graphics, DrawnFrame.SCREEN_WIDTH, DrawnFrame.SCREEN_HEIGHT, frame.targets);
            return frame;
        }

        /**
         * One press of [?] shadows the screen and lights one hole over each tab.
         */
        @Test
        void pressDrawsTheMaskWithAHolePerTab() {
            explainer.toggle();
            DrawnFrame frame = frame();
            assertFalse(fills(frame, DragMask.SHADOW).isEmpty(), "the shadow covers the screen");
            List<DragMask.Rect> tabs = java.util.Arrays.stream(ManagerTab.values()).map(tab ->
                    new DragMask.Rect(8 + tab.ordinal() * 50, 20, 56 + tab.ordinal() * 50, 34)).toList();
            assertEquals(tabs, fills(frame, DragMask.HIGHLIGHT));
        }

        /**
         * Hovering the Items tab in the mode opens a tooltip reading the Items paragraph, starting at its first
         * sentence.
         */
        @Test
        void hoveringItemsInTheModeShowsTheItemsParagraph() {
            explainer.toggle();
            DrawnFrame frame = frame();
            String key = frame.targets.hovered(20, 25).orElseThrow();
            String text = String.join(" ", explainer.tooltipFor(key).orElseThrow().stream()
                    .map(line -> ((TooltipLine.Text) line).text().getString()).toList());
            assertTrue(text.startsWith("Search for items, view their explicit or derived values, and recipe ratios"
                    + " to help make decisions on values."), text);
        }

        /**
         * A second press of [?] draws no shadow and hovers no explainer text.
         */
        @Test
        void secondPressDrawsNoMask() {
            explainer.toggle();
            explainer.toggle();
            DrawnFrame frame = frame();
            assertEquals(List.of(), fills(frame, DragMask.SHADOW));
            assertEquals(java.util.Optional.empty(), frame.targets.hovered(20, 25));
        }
    }

    /**
     * A left-button press carrying shift runs the follow action of the area under it and leaves its left action
     * unrun, a plain left-button press runs the left one, and a right-button press runs neither (decision
     * shift-left-click-follows-plain-left-modifies).
     */
    @Test
    void pressReachesTheActionForItsGesture() {
        List<String> ran = new ArrayList<>();
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 50, 10, () -> ran.add("modify"));
        targets.addFollow(0, 0, 50, 10, () -> ran.add("follow"), null);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT)));
        assertEquals(List.of("follow"), ran);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)));
        assertEquals(List.of("follow", "modify"), ran);
        ValuesManagerScreen.clickTargets(targets,
                new MouseButtonEvent(5, 5, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0)));
        assertEquals(List.of("follow", "modify"), ran);
    }

    /**
     * Two cursor positions over one hovered area place its tooltip at one point, the point the area's bounds anchor
     * (decision tooltips-anchor-below-their-element-and-grow-upward-when-short-of-room).
     */
    @Test
    void tooltipStaysPutAsTheCursorMovesOverOneElement() {
        ClickTargets targets = new ClickTargets();
        targets.addHover(40, 20, 50, 14, "stick");
        List<org.joml.Vector2ic> points = new ArrayList<>();
        for (int mouseX : new int[] {42, 85}) {
            int mouseY = mouseX == 42 ? 22 : 31;
            points.add(ValuesManagerScreen.anchoredTooltip(targets, mouseX, mouseY).orElseThrow()
                    .positionTooltip(400, 300, mouseX, mouseY, 60, 30));
        }
        TooltipAnchor.Point anchored = TooltipAnchor.place(new GroupsPane.Bounds(40, 20, 90, 34), 60, 30, 400, 300);
        assertEquals(List.of(new org.joml.Vector2i(anchored.x(), anchored.y()),
                new org.joml.Vector2i(anchored.x(), anchored.y())), points);
    }

    @Nested
    class RightClickOnATextBox {
        private final Font font = mock(Font.class);
        private final List<String> responded = new ArrayList<>();

        {
            when(font.plainSubstrByWidth(anyString(), anyInt())).thenAnswer(call -> call.getArgument(0));
            when(font.plainSubstrByWidth(anyString(), anyInt(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        }

        private EditBox box(int x, int y, String text) {
            EditBox box = new EditBox(font, x, y, 100, 12, Component.literal(""));
            box.setValue(text);
            box.setResponder(responded::add);
            return box;
        }

        private static MouseButtonEvent press(double x, double y, int button) {
            return new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
        }

        /**
         * A right-button press inside a box holding "iron" empties it, its responder takes the empty text and the
         * press is consumed (decision right-click-empties-every-text-box-in-the-manager).
         */
        @Test
        void rightPressInsideABoxEmptiesItAndRunsItsResponder() {
            EditBox search = box(0, 0, "iron");
            responded.clear();
            boolean took = ValuesManagerScreen.emptyBoxUnder(List.of(search),
                    press(5, 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
            assertTrue(took);
            assertEquals("", search.getValue());
            assertEquals(List.of(""), responded);
        }

        /**
         * Each of the manager's boxes, the Items search, the Prefabs search and rename, the New group name and the
         * builder's number and dropdown search, empties under its own right press and leaves the others whole.
         */
        @Test
        void rightPressEmptiesOnlyTheBoxUnderIt() {
            List<EditBox> boxes = List.of(box(0, 0, "items"), box(0, 20, "prefabs"), box(0, 40, "rename"),
                    box(0, 60, "group"), box(0, 80, "4"), box(0, 100, "dropdown"));
            for (int each = 0; each < boxes.size(); each++) {
                ValuesManagerScreen.emptyBoxUnder(boxes, press(5, each * 20 + 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT));
                assertEquals("", boxes.get(each).getValue());
                for (int later = each + 1; later < boxes.size(); later++) {
                    assertFalse(boxes.get(later).getValue().isEmpty());
                }
            }
        }

        /**
         * A right press no box covers, a left press inside a box and a right press on a hidden box keep the text and
         * pass the press on.
         */
        @Test
        void pressOutsideABoxOrOfAnotherButtonKeepsTheText() {
            EditBox search = box(0, 0, "iron");
            EditBox hidden = box(0, 20, "iron");
            hidden.visible = false;
            List<EditBox> boxes = List.of(search, hidden);
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(300, 5, GLFW.GLFW_MOUSE_BUTTON_RIGHT)));
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(5, 5, GLFW.GLFW_MOUSE_BUTTON_LEFT)));
            assertFalse(ValuesManagerScreen.emptyBoxUnder(boxes, press(5, 25, GLFW.GLFW_MOUSE_BUTTON_RIGHT)));
            assertEquals("iron", search.getValue());
            assertEquals("iron", hidden.getValue());
        }
    }

    /**
     * The screen's link cursor leaves each frame on the pointer hand while shift is held over a follow target, and on
     * the default cursor with shift up and over a target holding no follow action; as the screen is removed the
     * window takes the default back (decision shift-over-a-link-shows-a-pixel-pointer-hand).
     */
    @Test
    void handShowsOnlyWhileShiftIsHeldOverAFollowTarget() {
        CursorType hand = new CursorType("hand", 1L);
        List<CursorType> onWindow = new ArrayList<>();
        LinkCursor cursor = new LinkCursor(onWindow::add, () -> hand);
        ClickTargets targets = new ClickTargets();
        targets.add(0, 0, 100, 10, () -> { });
        targets.addFollow(0, 0, 50, 10, () -> { }, null);
        targets.holdShift(true);
        List<CursorType> frames = new ArrayList<>();
        frames.add(cursorOfFrame(cursor, targets, 5));
        frames.add(cursorOfFrame(cursor, targets, 75));
        targets.holdShift(false);
        frames.add(cursorOfFrame(cursor, targets, 5));
        assertEquals(List.of(hand, CursorType.DEFAULT, CursorType.DEFAULT), frames);
        cursor.release();
        assertEquals(List.of(CursorType.DEFAULT), onWindow);
    }

    // The cursor a frame applies once drawn: the default the frame starts with unless the cursor requested another,
    // as GuiGraphicsExtractor's pending cursor does.
    private static CursorType cursorOfFrame(LinkCursor cursor, ClickTargets targets, double x) {
        CursorType[] pending = {CursorType.DEFAULT};
        cursor.frame(targets, x, 5, requested -> pending[0] = requested);
        return pending[0];
    }

    @Nested
    class Opening {

        /**
         * Opening the manager reads the model and then the draft's resolve, uncovered, derived-from, groups,
         * constants and processes, and asks the server for no inventory (decision
         * client-builds-items-and-tags-from-its-own-registries).
         */
        @Test
        void openReadsTheModelAndTheDraftAndNoInventory() {
            List<Posted> requested = new ArrayList<>();
            ManagerActions actions = recordingActions(requested);
            reads(actions, new ManagerDraft()).open(new ListedEntrySource(List.of(), List.of()), () -> { });
            assertEquals(List.of(WorkbenchOperations.MODEL, WorkbenchOperations.RESOLVE_VALUES,
                    WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.DERIVED_FROM,
                    WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.EVALUATE_CONSTANTS,
                    WorkbenchOperations.PROCESSES), operations(requested));
        }
    }

    @Nested
    class Posting {
        private static final List<String> DRAFT_READS = List.of(WorkbenchOperations.RESOLVE_VALUES,
                WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.DERIVED_FROM,
                WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.EVALUATE_CONSTANTS,
                WorkbenchOperations.PROCESSES);

        private final List<Posted> requested = new ArrayList<>();
        private final ManagerDraft draft = new ManagerDraft();
        private final ValuesTab values = new ValuesTab(draft);
        private final ManagerReads reads = reads(recordingActions(requested), draft, values);

        // The members each draft read posted, by key, one list per read, in the order DRAFT_READS names them.
        private List<List<String>> postedKeys() {
            List<Posted> drafted = requested.stream().filter(each -> DRAFT_READS.contains(each.operation())).toList();
            assertEquals(DRAFT_READS, drafted.stream().map(Posted::operation).toList());
            List<List<String>> keys = drafted.stream().map(each -> JsonParser.parseString(each.text())
                    .getAsJsonObject().getAsJsonArray("changed").asList().stream()
                    .map(member -> member.getAsJsonObject().get("key").getAsString()).toList()).toList();
            requested.clear();
            return keys;
        }

        /**
         * The open's draft reads post no member, the refreshes after one edit post that member alone, and the
         * refreshes after the save answered post no member again (decision requests-post-only-the-changed-members).
         */
        @Test
        void draftReadsPostOnlyTheMembersChangedSinceTheSave() {
            reads.open(new ListedEntrySource(List.of(), List.of()), () -> { });
            assertEquals(List.of(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), postedKeys());
            values.readEntries(new ListedEntrySource(
                    List.of(new ManagerEntrySource.ItemName("minecraft:stick", "Stick")), List.of()));
            values.select("minecraft:stick");
            values.edit("5");
            reads.readAfresh();
            List<String> stick = List.of("stick");
            assertEquals(List.of(stick, stick, stick, stick, stick, stick), postedKeys());
            reads.save(() -> { });
            assertEquals(List.of(List.of("stick")), requested.stream()
                    .filter(each -> WorkbenchOperations.SAVE_MODEL.equals(each.operation()))
                    .map(each -> JsonParser.parseString(each.text()).getAsJsonObject().getAsJsonArray("changed")
                            .asList().stream().map(member -> member.getAsJsonObject().get("key").getAsString())
                            .toList()).toList());
            assertEquals(List.of(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), postedKeys());
        }
    }

    /**
     * One request the fake line took.
     *
     * @param operation the operation requested
     * @param text      the text it posted
     */
    private record Posted(String operation, String text) {
    }

    private static List<String> operations(List<Posted> requested) {
        return requested.stream().map(Posted::operation).toList();
    }

    // Answers the model from the canned replies and a save with the model the save posted merged, as the server
    // does, and records every operation requested with its posted text.
    private static ManagerActions recordingActions(List<Posted> requested) {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        doAnswer(call -> {
            String operation = call.getArgument(0);
            requested.add(new Posted(operation, call.getArgument(1)));
            String reply = WorkbenchOperations.SAVE_MODEL.equals(operation) ? SAVED : CANNED.get(operation);
            if (reply != null) {
                call.<Consumer<String>>getArgument(2).accept(reply);
            }
            return null;
        }).when(actions).request(anyString(), anyString(), any());
        return actions;
    }

    private static ManagerReads reads(ManagerActions actions, ManagerDraft draft) {
        ValuesTab values = new ValuesTab(draft);
        return reads(actions, draft, values);
    }

    private static ManagerReads reads(ManagerActions actions, ManagerDraft draft, ValuesTab values) {
        ItemIcons icons = mock(ItemIcons.class);
        GroupsPane groups = new GroupsPane(new GroupsTab(draft), values, icons, actions, builder(actions, icons));
        VariablesPane variables = new VariablesPane(new VariablesTab(draft), draft, icons, actions,
                builder(actions, icons));
        ProcessesPane processes = new ProcessesPane(new ProcessesTab(draft), draft, actions, icons, List::of);
        return new ManagerReads(actions, values, groups, variables, processes);
    }

    private static ExpressionBuilderPane builder(ManagerActions actions, ItemIcons icons) {
        return new ExpressionBuilderPane(actions, icons, List::of, List::of);
    }

    /**
     * A run of goo amount lines reaches the tooltip as one grid element between the text lines around it (decision
     * tooltip-goo-lists-fill-ceil-sqrt-columns-aligned-on-the-widest-digits).
     */
    @Test
    void aRunOfAmountLinesFoldsIntoOneGrid() {
        TooltipLine.GooAmount metal = new TooltipLine.GooAmount("metal", "3");
        TooltipLine.GooAmount rock = new TooltipLine.GooAmount("rock", "1/2");
        var elements = ValuesManagerScreen.tooltipElements(List.of(new TooltipLine.Text(Component.literal("stick")),
                metal, rock, new TooltipLine.Text(Component.literal("after"))));
        assertEquals(3, elements.size(), elements.toString());
        assertEquals(java.util.Optional.of(new TooltipLine.GooAmounts(List.of(metal, rock))), elements.get(1).right());
    }
}
