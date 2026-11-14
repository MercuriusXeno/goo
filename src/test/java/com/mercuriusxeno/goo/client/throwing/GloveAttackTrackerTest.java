package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the attack key does with the glove: a charge standing is cancelled
 * with no touch and no punch, a press with no charge standing touches as it
 * did, and a channeled Grab throws its held mob
 * (decisions abilities-not-channeled-charge-and-cancel-on-left-click,
 * attack-key-touches-plus-punches, grab-holds-and-throws-a-physics-body).
 */
class GloveAttackTrackerTest {

    @Test
    void anAttackKeyDuringAChargeCancelsItAndSendsNoTouch() {
        assertEquals(GloveAttackTracker.AttackOutcome.CANCEL_CHARGE,
                GloveAttackTracker.outcomeOf(true, false, true));
    }

    @Test
    void anAttackKeyWithNoChargeStandingSendsTheTouch() {
        assertEquals(GloveAttackTracker.AttackOutcome.TOUCH, GloveAttackTracker.outcomeOf(false, false, true));
    }

    @Test
    void anAttackKeyWithNoChargeAndNothingToTouchStaysVanilla() {
        assertEquals(GloveAttackTracker.AttackOutcome.VANILLA, GloveAttackTracker.outcomeOf(false, false, false));
    }

    @Test
    void anAttackKeyDuringAChanneledGrabThrowsTheHeldMob() {
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_GRAB, GloveAttackTracker.outcomeOf(false, true, true));
    }
}
