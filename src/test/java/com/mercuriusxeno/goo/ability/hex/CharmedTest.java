package com.mercuriusxeno.goo.ability.hex;

import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which hit breaks a charm: the charmer's alone, never a stranger's
 * or damage with no one behind it (decision charm-holds-until-struck).
 */
class CharmedTest {

    private static final UUID CHARMER = new UUID(1L, 1L);
    private static final UUID STRANGER = new UUID(2L, 2L);
    private static final Charmed CHARM = new Charmed(CHARMER);

    @Test
    void theCharmersHitBreaksTheCharm() {
        assertTrue(CHARM.brokenBy(CHARMER));
    }

    @Test
    void aStrangersHitLeavesTheCharm() {
        assertFalse(CHARM.brokenBy(STRANGER));
    }

    @Test
    void damageWithNoAttackerLeavesTheCharm() {
        assertFalse(CHARM.brokenBy(null));
    }
}
