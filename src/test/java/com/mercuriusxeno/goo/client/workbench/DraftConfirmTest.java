package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Promote and Discard confirms the manager's topbar opens, as the page's draft dialog runs them, with no
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
     * Discard asks naming the file it copies back, and its done line names it.
     */
    @Test
    void discardAsksNamingTheFileItCopiesBack() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askDiscard(PATH);
        assertEquals("Discard the draft?", confirm.dialog().orElseThrow().title());
        assertEquals(WorkbenchOperations.DISCARD, confirm.operation());
        confirm.confirmed();
        assertEquals("draft discarded", confirm.doneStatus(PATH));
        confirm.cancel();
        assertTrue(confirm.dialog().isEmpty());
    }
}
