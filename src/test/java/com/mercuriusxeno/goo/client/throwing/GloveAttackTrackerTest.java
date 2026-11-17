package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the attack key does with the glove: a charge standing is cancelled
 * with no touch and no punch, a press with no charge standing touches as it
 * did, and a channeled Grab throws its held mob
 * (decisions abilities-not-channeled-charge-and-cancel-on-left-click,
 * attack-key-touches-plus-punches, grab-holds-and-throws-a-physics-body), and a javelin right click
 * holds is thrown before any of them (decision dart-forms-on-right-click-and-throws-on-left-click).
 */
class GloveAttackTrackerTest {

    @Test
    void anAttackKeyDuringAChargeCancelsItAndSendsNoTouch() {
        assertEquals(GloveAttackTracker.AttackOutcome.CANCEL_CHARGE,
                GloveAttackTracker.outcomeOf(false, true, false, true));
    }

    @Test
    void anAttackKeyWithNoChargeStandingSendsTheTouch() {
        assertEquals(GloveAttackTracker.AttackOutcome.TOUCH, GloveAttackTracker.outcomeOf(false, false, false, true));
    }

    @Test
    void anAttackKeyWithNoChargeAndNothingToTouchStaysVanilla() {
        assertEquals(GloveAttackTracker.AttackOutcome.VANILLA, GloveAttackTracker.outcomeOf(false, false, false, false));
    }

    @Test
    void anAttackKeyDuringAChanneledGrabThrowsTheHeldMob() {
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_GRAB, GloveAttackTracker.outcomeOf(false, false, true, true));
    }

    @Test
    void anAttackKeyDuringAKnivesHoldResolvesToTheHeldThrow() {
        // knives-form-on-right-click-and-throw-on-left-click: a Knives hold is a held throw's press
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_JAVELIN, GloveAttackTracker.outcomeOf(true, true, false,
                false));
    }

    @Test
    void anAttackKeyWhileRightClickHoldsAJavelinThrowsItAndCancelsNothing() {
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_JAVELIN, GloveAttackTracker.outcomeOf(true, true, false, true));
    }
}
