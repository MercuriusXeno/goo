package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.jspecify.annotations.Nullable;
import java.util.Optional;

/**
 * The Promote and Revert confirms the manager's topbar opens, as the page's draft dialog runs them: Promote asks
 * once, naming the file it overwrites and saving unsaved changes first, and Revert names the file it copies back and
 * runs the workbench's Discard (decisions save-applies-the-draft-live-promote-renames-it,
 * values-checks-go-with-the-checks-tab, topbar-discard-reads-revert).
 */
public final class DraftConfirm {
    private static final String EMPTY_REQUEST = "{}";
    private static final String PROMOTE_TITLE = "Promote the draft?";
    private static final String PROMOTE_START = "This will overwrite the value file at ";
    private static final String PROMOTE_END = " with the draft.";
    private static final String PROMOTE_SAVING_END = " with the draft, saving your unsaved changes into the draft first.";
    private static final String REVERT_TITLE = "Revert the draft?";
    private static final String REVERT_START = "This copies ";
    private static final String REVERT_END = " back over the draft: every change not yet promoted, saved or unsaved, "
            + "is lost.";
    private static final String PROMOTED = "promoted into ";
    private static final String REVERTED = "draft reverted";

    private @Nullable String operation;
    private @Nullable Dialog dialog;
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
        unsaved = pending;
        dialog = new Dialog(PROMOTE_TITLE, PROMOTE_START + path + (pending ? PROMOTE_SAVING_END : PROMOTE_END));
    }

    /**
     * @param path the value file Revert copies back
     */
    public void askRevert(String path) {
        operation = WorkbenchOperations.DISCARD;
        unsaved = false;
        dialog = new Dialog(REVERT_TITLE, REVERT_START + path + REVERT_END);
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
     * The player cancelled, or the request answered: nothing more runs.
     */
    public void cancel() {
        dialog = null;
        operation = null;
    }

    /**
     * @return whether the request waits on a save of the unsaved changes first
     */
    public boolean savesFirst() {
        return WorkbenchOperations.PROMOTE.equals(operation) && unsaved;
    }

    /**
     * @return the operation the confirm runs
     */
    public String operation() {
        return operation == null ? WorkbenchOperations.DISCARD : operation;
    }

    /**
     * @return the request's text, which carries nothing beyond the operation
     */
    public String requestText() {
        return EMPTY_REQUEST;
    }

    /**
     * @param path the value file the action left standing
     * @return the status line the done action leaves
     */
    public String doneStatus(String path) {
        return WorkbenchOperations.PROMOTE.equals(operation) ? PROMOTED + path : REVERTED;
    }
}
