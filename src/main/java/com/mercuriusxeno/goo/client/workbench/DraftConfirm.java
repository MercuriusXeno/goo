package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The Promote and Discard confirms the manager's topbar opens, as the page's draft dialog runs them: Promote names
 * the file it overwrites and saves unsaved changes first, a promote the checks refuse asks again listing them and
 * resends acknowledging them, and Discard names the file it copies back (decisions
 * save-applies-the-draft-live-promote-renames-it, type-source-checks-are-rules-that-gate-promote).
 */
public final class DraftConfirm {
    private static final int CONFLICT = 409;
    private static final String ACKNOWLEDGED = "acknowledged";
    private static final String FAILING = "failing";
    private static final String TITLE = "title";
    private static final String WANTS = "wants";
    private static final String ITEM = "item";
    private static final String OBJECT_START = "{";
    private static final String PROMOTE_TITLE = "Promote the draft?";
    private static final String PROMOTE_START = "This will overwrite the value file at ";
    private static final String PROMOTE_END = " with the draft.";
    private static final String PROMOTE_SAVING_END = " with the draft, saving your unsaved changes into the draft first.";
    private static final String DISCARD_TITLE = "Discard the draft?";
    private static final String DISCARD_START = "This copies ";
    private static final String DISCARD_END = " back over the draft: every change not yet promoted, saved or unsaved, "
            + "is lost.";
    private static final String CHECK_SINGULAR = " check fails on the draft";
    private static final String CHECKS_PLURAL = " checks fail on the draft";
    private static final String ANYWAY_START = "Promote anyway? It will overwrite the value file at ";
    private static final String ANYWAY_MID = " though these fail: ";
    private static final String WANTS_START = " (wants ";
    private static final String WANTS_END = ")";
    private static final String ITEM_SEPARATOR = ", ";
    private static final String CHECK_SEPARATOR = "; ";
    private static final String SENTENCE_END = ".";
    private static final String PROMOTED = "promoted into ";
    private static final String DISCARDED = "draft discarded";

    private @Nullable String operation;
    private @Nullable Dialog dialog;
    private boolean acknowledging;
    private boolean unsaved;

    /**
     * What the confirm says.
     *
     * @param title its title
     * @param text  its body
     */
    public record Dialog(String title, String text) {
    }

    /**
     * @param path    the value file Promote overwrites
     * @param pending whether the draft holds changes not yet saved
     */
    public void askPromote(String path, boolean pending) {
        operation = WorkbenchOperations.PROMOTE;
        acknowledging = false;
        unsaved = pending;
        dialog = new Dialog(PROMOTE_TITLE, PROMOTE_START + path + (pending ? PROMOTE_SAVING_END : PROMOTE_END));
    }

    /**
     * @param path the value file Discard copies back
     */
    public void askDiscard(String path) {
        operation = WorkbenchOperations.DISCARD;
        acknowledging = false;
        unsaved = false;
        dialog = new Dialog(DISCARD_TITLE, DISCARD_START + path + DISCARD_END);
    }

    /**
     * @return the confirm standing open, empty while none is
     */
    public Optional<Dialog> dialog() {
        return Optional.ofNullable(dialog);
    }

    /**
     * The player confirmed: the confirm closes while its request runs.
     */
    public void confirmed() {
        dialog = null;
    }

    /**
     * The player cancelled: nothing runs.
     */
    public void cancel() {
        dialog = null;
        operation = null;
        acknowledging = false;
    }

    /**
     * @return whether the request waits on a save of the unsaved changes first
     */
    public boolean savesFirst() {
        return WorkbenchOperations.PROMOTE.equals(operation) && unsaved && !acknowledging;
    }

    /**
     * @return the operation the confirm runs
     */
    public String operation() {
        return operation == null ? WorkbenchOperations.DISCARD : operation;
    }

    /**
     * @return the request's text, acknowledging failing checks on the second confirm
     */
    public String requestText() {
        JsonObject request = new JsonObject();
        request.addProperty(ACKNOWLEDGED, acknowledging);
        return request.toString();
    }

    /**
     * Reads a refused request: a promote the checks refuse opens the second confirm listing them.
     *
     * @param status the reply's status
     * @param text   the reply's text
     * @param path   the value file Promote overwrites
     * @return whether the confirm opened again, else the refusal ends the action
     */
    public boolean readRefusal(int status, String text, String path) {
        boolean failingChecks = status == CONFLICT && WorkbenchOperations.PROMOTE.equals(operation) && !acknowledging
                && text.trim().startsWith(OBJECT_START);
        if (!failingChecks) {
            cancel();
            return false;
        }
        List<JsonObject> failing = JsonParser.parseString(text).getAsJsonObject().getAsJsonArray(FAILING).asList()
                .stream().map(JsonElement::getAsJsonObject).toList();
        String listed = failing.stream().map(check -> check.get(TITLE).getAsString() + WANTS_START
                + check.getAsJsonArray(WANTS).asList().stream()
                        .map(want -> want.getAsJsonObject().get(ITEM).getAsString())
                        .collect(Collectors.joining(ITEM_SEPARATOR)) + WANTS_END)
                .collect(Collectors.joining(CHECK_SEPARATOR));
        acknowledging = true;
        dialog = new Dialog(failing.size() + (failing.size() == 1 ? CHECK_SINGULAR : CHECKS_PLURAL),
                ANYWAY_START + path + ANYWAY_MID + listed + SENTENCE_END);
        return true;
    }

    /**
     * @param path the value file the action left standing
     * @return the status line the done action leaves
     */
    public String doneStatus(String path) {
        return WorkbenchOperations.PROMOTE.equals(operation) ? PROMOTED + path : DISCARDED;
    }
}
