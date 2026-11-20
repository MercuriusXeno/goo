package com.mercuriusxeno.goo.client.throwing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the attack key does with the glove: a charge standing is cancelled,
 * a mob ability's press on a mob is vanilla's melee hit, and a channeled Grab
 * throws its held mob
 * (decisions abilities-not-channeled-charge-and-cancel-on-left-click,
 * attack-key-touches-plus-punches, grab-holds-and-throws-a-physics-body), and a javelin right click
 * holds is thrown before any of them (decision dart-forms-on-right-click-and-throws-on-left-click); with Portal
 * selected the press shoots the blue portal before anything else, once a press
 * (decision portal-takes-over-left-and-right-click-for-blue-and-orange).
 */
class GloveAttackTrackerTest {

    @Test
    void anAttackKeyDuringAChargeCancelsIt() {
        assertEquals(GloveAttackTracker.AttackOutcome.CANCEL_CHARGE,
                GloveAttackTracker.outcomeOf(false, false, true, false));
    }

    /** A mob ability's press on a mob within reach is vanilla's melee hit (decision attack-key-touches-plus-punches). */
    @Test
    void aMobAbilityPressOnAMobWithinReachIsTheVanillaHit() {
        assertEquals(GloveAttackTracker.AttackOutcome.VANILLA, GloveAttackTracker.outcomeOf(false, false, false, false));
    }

    @Test
    void anAttackKeyDuringAChanneledGrabThrowsTheHeldMob() {
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_GRAB, GloveAttackTracker.outcomeOf(false, false, false, true));
    }

    @Test
    void anAttackKeyDuringAKnivesHoldResolvesToTheHeldThrow() {
        // knives-form-on-right-click-and-throw-on-left-click: a Knives hold is a held throw's press
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_JAVELIN, GloveAttackTracker.outcomeOf(false, true, true, false));
    }

    @Test
    void anAttackKeyWhileRightClickHoldsAJavelinThrowsItAndCancelsNothing() {
        assertEquals(GloveAttackTracker.AttackOutcome.THROW_JAVELIN, GloveAttackTracker.outcomeOf(false, true, true, false));
    }

    @Test
    void anAttackKeyWithPortalSelectedShootsThePortalWhileRightClickCharges() {
        assertEquals(GloveAttackTracker.AttackOutcome.SHOOT_PORTAL, GloveAttackTracker.outcomeOf(true, false, true,
                false));
    }
}
