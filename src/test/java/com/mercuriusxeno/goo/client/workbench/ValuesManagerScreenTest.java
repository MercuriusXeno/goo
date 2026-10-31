package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import com.mojang.blaze3d.platform.cursor.CursorType;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
     * The X is a square ending at the right margin and the topbar starts one gap left of it (decision
     * x-in-the-top-right-closes-the-manager).
     */
    @Test
    void closeXEndsAtTheRightMarginAndTheTopbarStandsLeftOfIt() {
        ValuesManagerScreen.TitleRow row = ValuesManagerScreen.titleRowOf(400);
        assertEquals(new ValuesManagerScreen.TitleRow(378, 392, 376), row);
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
}
