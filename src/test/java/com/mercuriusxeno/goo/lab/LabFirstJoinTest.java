package com.mercuriusxeno.goo.lab;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads the gate the login handler puts before the Goo Lab save's first join: a dev launch on the save named Goo Lab.
 */
class LabFirstJoinTest {

    private static final boolean DEV = false;
    private static final boolean PRODUCTION = true;

    @Test
    void devLaunchOnTheGooLabSaveFitsOut() {
        assertTrue(LabFirstJoin.fitsOut(DEV, "Goo Lab"));
    }

    @Test
    void productionLaunchOnTheGooLabSaveFitsNothingOut() {
        assertFalse(LabFirstJoin.fitsOut(PRODUCTION, "Goo Lab"));
    }

    @Test
    void devLaunchOnAnotherSaveFitsNothingOut() {
        assertFalse(LabFirstJoin.fitsOut(DEV, "New World"));
    }
}
