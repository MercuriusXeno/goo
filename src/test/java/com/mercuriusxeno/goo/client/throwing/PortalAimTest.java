package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With Portal held, a button shoots once a press: a fresh press shoots and
 * the interaction vanilla repeats while the button stays down shoots no more
 * (operator UAT 2026-11-17: both buttons fire on press).
 */
class PortalAimTest {

    @Test
    void aFreshPressShootsPortal() {
        assertTrue(PortalAim.shootsOnThisPress(false));
    }

    @Test
    void aButtonHeldDownShootsPortalNoMore() {
        assertFalse(PortalAim.shootsOnThisPress(true));
    }
}