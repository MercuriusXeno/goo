package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * The Promote and Revert confirms the manager's topbar opens, as the page's draft dialog runs them: Promote asks
 * once, naming the file it overwrites and saving unsaved changes first, and Revert names the file it copies back and
 * runs the workbench's Discard (decisions save-applies-the-draft-live-promote-renames-it,
 * values-checks-go-with-the-checks-tab, topbar-discard-reads-revert). A close with unsaved changes asks Save and
 * close, Close without saving or Cancel, since the changes live only in the screen until a Save posts them.
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
    private static final String CLOSE_TITLE = "Close with unsaved changes?";
    private static final String CLOSE_TEXT = "Your changes since the last Save are not in the draft yet. "
            + "Closing without saving loses them.";
    private static final List<Choice> DRAFT_ACTION_CHOICES = List.of(Choice.CONFIRM, Choice.CANCEL);
    private static final List<Choice> CLOSE_CHOICES = List.of(Choice.SAVE_AND_CLOSE, Choice.CLOSE_UNSAVED,
            Choice.CANCEL);

    private @Nullable String operation;
    private @Nullable Dialog dialog;
    private boolean unsaved;
    private boolean closing;

    /**
     * One button a confirm offers.
     */
    public enum Choice {
        /** Runs the Promote or Revert asked. */
        CONFIRM("Confirm"),
        /** Saves the unsaved changes into the draft, then closes the manager. */
        SAVE_AND_CLOSE("Save and close"),
        /** Closes the manager, losing the unsaved changes. */
        CLOSE_UNSAVED("Close without saving"),
        /** Closes the confirm and runs nothing. */
        CANCEL("Cancel");

        private final String label;

        Choice(String label) {
            this.label = label;
        }

        /**
         * @return what its button reads
         */
        public String label() {
            return label;
        }
    }

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
        closing = false;
        dialog = new Dialog(PROMOTE_TITLE, PROMOTE_START + path + (pending ? PROMOTE_SAVING_END : PROMOTE_END));
    }

    /**
     * @param path the value file Revert copies back
     */
    public void askRevert(String path) {
        operation = WorkbenchOperations.DISCARD;
        unsaved = false;
        closing = false;
        dialog = new Dialog(REVERT_TITLE, REVERT_START + path + REVERT_END);
    }

    /**
     * A close asked of the manager, by its X or Esc: a standing confirm cancels and the manager stays, unsaved
     * changes stand the close confirm rather than being lost, and nothing unsaved closes at once.
     *
     * @param pending whether the draft holds changes not yet saved
     * @return whether the manager closes now
     */
    public boolean closesNow(boolean pending) {
        if (dialog != null) {
            cancel();
            return false;
        }
        if (!pending) {
            return true;
        }
        closing = true;
        dialog = new Dialog(CLOSE_TITLE, CLOSE_TEXT);
        return false;
    }

    /**
     * @return the buttons the standing confirm offers, in the order they draw
     */
    public List<Choice> choices() {
        return closing ? CLOSE_CHOICES : DRAFT_ACTION_CHOICES;
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
        closing = false;
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
