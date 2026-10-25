package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.WorkbenchOperations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the Promote and Discard confirms the manager's topbar opens, as the page's draft dialog runs them, with no
 * Screen (decisions save-applies-the-draft-live-promote-renames-it, type-source-checks-are-rules-that-gate-promote).
 */
class DraftConfirmTest {

    private static final String PATH = "world/datapacks/goo_values/data/goo/goo_values/base_values.json";
    private static final String REFUSAL = """
            {"failing": [{"title": "every goo type has a source", "passed": false,
                          "wants": [{"item": "infinity", "lacks": "no item grants it"},
                                    {"item": "xeno", "lacks": "no item grants it"}]}]}
            """;

    /**
     * Promote asks first, naming the file it overwrites and saving unsaved changes first; its request carries no
     * acknowledgement.
     */
    @Test
    void promoteAsksNamingTheFileAndSavesFirst() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askPromote(PATH, true);
        assertEquals("Promote the draft?", confirm.dialog().orElseThrow().title());
        assertEquals("This will overwrite the value file at " + PATH
                + " with the draft, saving your unsaved changes into the draft first.",
                confirm.dialog().orElseThrow().text());
        assertTrue(confirm.savesFirst());
        assertEquals(WorkbenchOperations.PROMOTE, confirm.operation());
        assertEquals("{\"acknowledged\":false}", confirm.requestText());
    }

    /**
     * A promote the checks refuse reopens the confirm listing each failing check with the items it wants, and the
     * resend acknowledges them.
     */
    @Test
    void refusedPromoteAsksAgainListingTheFailingChecks() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askPromote(PATH, false);
        confirm.confirmed();
        assertTrue(confirm.readRefusal(409, REFUSAL, PATH));
        assertEquals("1 check fails on the draft", confirm.dialog().orElseThrow().title());
        assertEquals("Promote anyway? It will overwrite the value file at " + PATH
                + " though these fail: every goo type has a source (wants infinity, xeno).",
                confirm.dialog().orElseThrow().text());
        assertEquals("{\"acknowledged\":true}", confirm.requestText());
        assertFalse(confirm.savesFirst());
    }

    /**
     * Discard asks naming the file it copies back, a refusal of it reopens nothing, and its done line names it.
     */
    @Test
    void discardAsksAndAnyRefusalEndsIt() {
        DraftConfirm confirm = new DraftConfirm();
        confirm.askDiscard(PATH);
        assertEquals("Discard the draft?", confirm.dialog().orElseThrow().title());
        assertEquals(WorkbenchOperations.DISCARD, confirm.operation());
        confirm.confirmed();
        assertFalse(confirm.readRefusal(409, "No live file stands", PATH));
        assertEquals("draft discarded", confirm.doneStatus(PATH));
        confirm.cancel();
        assertTrue(confirm.dialog().isEmpty());
    }
}
