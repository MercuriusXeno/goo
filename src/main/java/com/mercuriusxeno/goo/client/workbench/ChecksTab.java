package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * The Checks tab's state with no Screen: each type-source rule the server ran against the draft on its last read or
 * save, pass or FAIL, a failing rule with the items it wants, and the tab's badge counting the failing rules, as
 * index.html's checks section holds them (decisions goo-workbench-opens-the-in-game-manager-screen,
 * type-source-checks-are-rules-that-gate-promote).
 */
public final class ChecksTab {
    private static final String CHECKS = "checks";
    private static final String TITLE = "title";
    private static final String PASSED = "passed";
    private static final String WANTS = "wants";
    private static final String ITEM = "item";
    private static final String LACKS = "lacks";
    private static final String PASS_MARK = "pass  ";
    private static final String FAIL_MARK = "FAIL  ";
    private static final String WANT_SEPARATOR = ": ";
    private static final String BADGE_START = " (";
    private static final String BADGE_END = " red)";

    private final ManagerDraft draft;

    /**
     * @param draft the draft whose last model reply carried the checks
     */
    public ChecksTab(ManagerDraft draft) {
        this.draft = draft;
    }

    /**
     * One rule.
     *
     * @param title  the rule's title, marked pass or FAIL
     * @param passed whether the draft meets it
     * @param wants  each item a failing rule wants and what it lacks
     */
    public record CheckRow(String title, boolean passed, List<String> wants) {
    }

    /**
     * @return each rule in the order the server ran them
     */
    public List<CheckRow> rows() {
        return draft.array(CHECKS).asList().stream().map(JsonElement::getAsJsonObject).map(ChecksTab::rowOf)
                .toList();
    }

    private static CheckRow rowOf(JsonObject check) {
        boolean passed = check.get(PASSED).getAsBoolean();
        List<String> wants = passed ? List.of() : check.getAsJsonArray(WANTS).asList().stream()
                .map(JsonElement::getAsJsonObject)
                .map(want -> want.get(ITEM).getAsString() + WANT_SEPARATOR + want.get(LACKS).getAsString()).toList();
        return new CheckRow((passed ? PASS_MARK : FAIL_MARK) + check.get(TITLE).getAsString(), passed, wants);
    }

    /**
     * @param label the tab's label
     * @return the label with its count of failing rules, bare where none fails
     */
    public String badge(String label) {
        long failing = rows().stream().filter(row -> !row.passed()).count();
        return failing == 0 ? label : label + BADGE_START + failing + BADGE_END;
    }
}
