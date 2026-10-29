package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import net.minecraft.client.gui.components.AbstractWidget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    @Nested
    class Opening {

        /**
         * Opening the manager reads the model and then the draft's resolve, uncovered, derived-from, groups,
         * constants and processes, and asks the server for no inventory (decision
         * client-builds-items-and-tags-from-its-own-registries).
         */
        @Test
        void openReadsTheModelAndTheDraftAndNoInventory() {
            List<String> requested = new ArrayList<>();
            ManagerActions actions = recordingActions(requested);
            reads(actions).open(new ListedEntrySource(List.of(), List.of()), () -> { });
            assertEquals(List.of(WorkbenchOperations.MODEL, WorkbenchOperations.RESOLVE_VALUES,
                    WorkbenchOperations.UNCOVERED_VALUES, WorkbenchOperations.DERIVED_FROM,
                    WorkbenchOperations.DESCRIBE_GROUPS, WorkbenchOperations.EVALUATE_CONSTANTS,
                    WorkbenchOperations.PROCESSES), requested);
        }
    }

    // Answers the model from the canned replies, and records every operation requested.
    private static ManagerActions recordingActions(List<String> requested) {
        ManagerActions actions = mock(ManagerActions.class);
        when(actions.addWidget(any())).thenAnswer(call -> call.<AbstractWidget>getArgument(0));
        doAnswer(call -> {
            String operation = call.getArgument(0);
            requested.add(operation);
            String reply = CANNED.get(operation);
            if (reply != null) {
                call.<java.util.function.Consumer<String>>getArgument(2).accept(reply);
            }
            return null;
        }).when(actions).request(anyString(), anyString(), any());
        return actions;
    }

    private static ManagerReads reads(ManagerActions actions) {
        ManagerDraft draft = new ManagerDraft();
        ValuesTab values = new ValuesTab(draft);
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
