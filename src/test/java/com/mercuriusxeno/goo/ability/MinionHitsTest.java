package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which hits the minion guard cancels: a minion's hit on a minion,
 * and no other (decision diagnose-and-correct-minions-killing-each-other).
 */
class MinionHitsTest {

    @Test
    void cancelsAMinionsHitOnAMinion() {
        assertTrue(MinionHits.cancels(true, true));
    }

    @Test
    void landsAMinionsHitOnAMobThatIsNoMinion() {
        assertFalse(MinionHits.cancels(true, false));
    }

    @Test
    void landsAHitFromAMobThatIsNoMinionOnAMinion() {
        assertFalse(MinionHits.cancels(false, true));
    }
}
