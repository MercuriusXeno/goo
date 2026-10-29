package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;

/**
 * The requests the values manager sends to read the draft: the open's model read, then every tab's read of the
 * draft afresh (decision goo-workbench-opens-the-in-game-manager-screen).
 */
final class ManagerReads {
    private static final String NOTHING_POSTED = "";

    private final ManagerActions actions;
    private final ValuesTab values;
    private final GroupsPane groups;
    private final VariablesPane variables;
    private final ProcessesPane processes;

    /**
     * @param actions   the line the requests go out on
     * @param values    the Values tab, which reads the model and its resolution
     * @param groups    the Groups pane, which describes the draft's groups
     * @param variables the Variables pane, which evaluates the draft's constants
     * @param processes the Processes pane, which describes the draft's processes
     */
    ManagerReads(ManagerActions actions, ValuesTab values, GroupsPane groups, VariablesPane variables,
                 ProcessesPane processes) {
        this.actions = actions;
        this.values = values;
        this.groups = groups;
        this.variables = variables;
        this.processes = processes;
    }

    /**
     * Reads the model, lists the client's items and tags, and reads the draft afresh.
     *
     * @param entries the client's items and item tags
     * @param opened  runs once the model answered
     */
    void open(ManagerEntrySource entries, Runnable opened) {
        actions.request(WorkbenchOperations.MODEL, NOTHING_POSTED, model -> {
            values.readModel(model);
            // decision client-builds-items-and-tags-from-its-own-registries
            values.readEntries(entries);
            opened.run();
            readAfresh();
        });
    }

    /**
     * Has every tab read the draft afresh: the Values tab's resolve, uncovered items and derived-from, the Groups
     * tab's describe, the Variables tab's evaluate and the Processes tab's description.
     */
    void readAfresh() {
        actions.request(WorkbenchOperations.RESOLVE_VALUES, values.modelText(), values::readResolved);
        actions.request(WorkbenchOperations.UNCOVERED_VALUES, values.modelText(), values::readUncovered);
        actions.request(WorkbenchOperations.DERIVED_FROM, values.modelText(), values::readDerivedFrom);
        groups.refresh();
        variables.refresh();
        processes.refresh();
    }
}
