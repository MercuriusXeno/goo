package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Promote and Revert confirms the manager's topbar opens, as the page's draft dialog runs them, with no
 * Screen (decisions save-applies-the-draft-live-promote-renames-it, values-checks-go-with-the-checks-tab).
 */
class DraftConfirmTest {

    private static final String PATH = "world/datapacks/goo_values/data/goo/goo_values/base_values.json";

    /**
     * Promote asks once, naming the file it overwrites and saving unsaved changes first; its request carries no
     * acknowledged member.
     */
    @Test
    void promoteAsksOnceNamingTheFileAndSavesFirst() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askPromote(PATH, true);
        assertEquals("Promote the draft?", confirm.dialog().orElseThrow().title());
        assertEquals("This will overwrite the value file at " + PATH
                + " with the draft, saving your unsaved changes into the draft first.",
                confirm.dialog().orElseThrow().text());
        assertTrue(confirm.savesFirst());
        assertEquals(WorkbenchOperations.PROMOTE, confirm.operation());
        assertFalse(JsonParser.parseString(confirm.requestText()).getAsJsonObject().has("acknowledged"),
                confirm.requestText());
    }

    /**
     * Revert asks naming the file it copies back and reads as Revert, while the operation it runs stays the
     * workbench's Discard (decision topbar-discard-reads-revert).
     */
    @Test
    void revertAsksNamingTheFileItCopiesBack() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askRevert(PATH);
        assertEquals("Revert the draft?", confirm.dialog().orElseThrow().title());
        assertTrue(confirm.dialog().orElseThrow().text().contains(PATH + " back over the draft"),
                confirm.dialog().orElseThrow().text());
        assertEquals(WorkbenchOperations.DISCARD, confirm.operation());
        confirm.confirmed();
        assertEquals("draft reverted", confirm.doneStatus(PATH));
        confirm.cancel();
        assertTrue(confirm.dialog().isEmpty());
    }

    /**
     * Closing the manager with changes not yet saved stands a confirm offering Save and close, Close without saving
     * and Cancel, rather than closing and losing them.
     */
    @Test
    void closeWithUnsavedChangesAsksSaveDiscardOrCancel() {
        DraftConfirm confirm = new DraftConfirm();
        assertFalse(confirm.closesNow(true));
        assertEquals("Close with unsaved changes?", confirm.dialog().orElseThrow().title());
        assertEquals(List.of(DraftConfirm.Choice.SAVE_AND_CLOSE, DraftConfirm.Choice.CLOSE_UNSAVED,
                DraftConfirm.Choice.CANCEL), confirm.choices());
        assertEquals(List.of("Save and close", "Close without saving", "Cancel"),
                confirm.choices().stream().map(DraftConfirm.Choice::label).toList());
    }

    /**
     * Closing with nothing unsaved closes at once and stands no confirm.
     */
    @Test
    void closeWithNothingUnsavedClosesAtOnce() {
        DraftConfirm confirm = new DraftConfirm();
        assertTrue(confirm.closesNow(false));
        assertTrue(confirm.dialog().isEmpty());
    }

    /**
     * A close while a confirm stands, as Esc on it, cancels the confirm and leaves the manager open.
     */
    @Test
    void closeWhileAConfirmStandsCancelsIt() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.closesNow(true);
        assertFalse(confirm.closesNow(true));
        assertTrue(confirm.dialog().isEmpty());
    }

    /**
     * Promote and Revert offer Confirm and Cancel.
     */
    @Test
    void promoteOffersConfirmAndCancel() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askPromote(PATH, false);
        assertEquals(List.of(DraftConfirm.Choice.CONFIRM, DraftConfirm.Choice.CANCEL), confirm.choices());
    }

    /**
     * The manager's topbar reads Save, Revert, Promote, laid leftward from the title row's right edge (decision
     * topbar-discard-reads-revert).
     */
    @Test
    void topbarReadsSaveRevertPromote() {
        assertEquals(List.of("Save", "Revert", "Promote"), ValuesManagerScreen.topbarLabels());
    }
}
