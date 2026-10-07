package com.mercuriusxeno.goo.ability.hearts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The overlay's rules in half hearts: laying shields, draining and burning hits, quenching, regrowing and expiring, for Kindle and Barkskin (decisions overlay-hearts-are-an-elemental-overshield, aggravated-damage-is-a-per-heart-rule, kindle-ember-hearts-ash-and-retaliate, barkskin-bark-hearts-thorn-and-burn). */
class HeartOverlayTest {

    private static final long NOW = 1_000L;
    private static final int DURATION = 1_200;
    private static final float FULL_HEALTH = 20f;
    private static final int FULL_HALVES = 20;
    private static final float DELTA = 1e-6f;

    private static HeartOverlay laid(HeartKind kind, float health) {
        return HeartOverlay.NONE.apply(kind, DURATION, health, NOW);
    }

    private static HeartOverlay kindled(float health) {
        return laid(HeartKind.KINDLE, health);
    }

    private static HeartOverlay barked(float health) {
        return laid(HeartKind.BARKSKIN, health);
    }

    /** A Kindle overlay over ten slots with the given halves of ember, leftmost first. */
    private static HeartOverlay kindledWithHalves(int halves, long regrowAt) {
        List<Integer> shields = new ArrayList<>(Collections.nCopies(10, 0));
        for (int slot = 0; halves > 0; slot++) {
            int here = Math.min(HeartOverlay.FULL_SHIELD, halves);
            shields.set(slot, here);
            halves -= here;
        }
        return new HeartOverlay(HeartKind.KINDLE, shields, NOW + DURATION, regrowAt, NOW);
    }

    @Nested
    class Apply {

        @Test
        void everyPresentHeartTakesAFullShield() {
            HeartOverlay overlay = kindled(FULL_HEALTH);
            assertEquals(FULL_HALVES, overlay.shieldHalves());
            assertEquals(NOW + DURATION, overlay.expiresAt());
        }

        @Test
        void missingHeartStaysMissing() {
            HeartOverlay overlay = kindled(13f);
            assertEquals(14, overlay.shieldHalves());
            assertEquals(0, overlay.shieldAt(7));
        }

        @Test
        void sameKindAgainAddsDurationAndKeepsHearts() {
            HeartOverlay broken = kindled(FULL_HEALTH).drain(1f, NOW).overlay();
            HeartOverlay stacked = broken.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW);
            assertEquals(NOW + 2L * DURATION, stacked.expiresAt());
            assertEquals(broken.shields(), stacked.shields());
        }

        @Test
        void anotherKindReplacesTheStandingOverlayWhole() {
            HeartOverlay spent = kindled(FULL_HEALTH).drain(5f, NOW).overlay()
                    .burn(1f, FULL_HEALTH, NOW + 1).overlay();
            HeartOverlay barked = spent.apply(HeartKind.BARKSKIN, DURATION, 18f, NOW + 2);
            assertEquals(HeartKind.BARKSKIN, barked.kind());
            assertEquals(Collections.nCopies(9, HeartOverlay.FULL_SHIELD), barked.shields());
            assertEquals(NOW + 2 + DURATION, barked.expiresAt());
            assertEquals(NOW + 2, barked.fireReadyAt());
        }
    }

    @Nested
    class Drain {

        @Test
        void halfAHeartOfDamageStripsHalfAShield() {
            HeartOverlay.Drained drained = kindled(FULL_HEALTH).drain(1f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(1, drained.overlay().shieldAt(9));
            assertEquals(2, drained.overlay().shieldAt(8));
        }

        @Test
        void aBiggerHitStripsHalvesRightmostFirst() {
            HeartOverlay.Drained drained = kindled(FULL_HEALTH).drain(3f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(0, drained.overlay().shieldAt(9));
            assertEquals(1, drained.overlay().shieldAt(8));
        }

        @Test
        void hitPastEveryShieldCostsAshDouble() {
            HeartOverlay.Drained drained = kindledWithHalves(1, NOW).drain(3f, NOW);
            assertEquals(4f, drained.remainder(), DELTA);
            assertEquals(0, drained.overlay().shieldHalves());
        }

        @Test
        void noOverlayPassesTheHitWhole() {
            assertEquals(5f, HeartOverlay.NONE.drain(5f, NOW).remainder(), DELTA);
        }

        @Test
        void strippedShieldRestartsTheRegrowClock() {
            HeartOverlay drained = kindled(FULL_HEALTH).drain(1f, NOW + 5).overlay();
            assertEquals(NOW + 5 + HeartKind.KINDLE.regrowInterval(19), drained.regrowAt());
        }
    }

    @Nested
    class Tick {

        @Test
        void overlayEndsAtExpiry() {
            assertSame(HeartOverlay.NONE, kindled(FULL_HEALTH).tick(FULL_HEALTH, false, NOW + DURATION));
        }

        @Test
        void waterTurnsEveryEmberToAsh() {
            HeartOverlay quenched = kindled(FULL_HEALTH).tick(FULL_HEALTH, true, NOW + 1);
            assertEquals(0, quenched.shieldHalves());
            assertEquals(NOW + 1 + HeartKind.KINDLE.regrowInterval(0), quenched.regrowAt());
        }

        @Test
        void ashWaitsForItsInterval() {
            HeartOverlay ash = kindledWithHalves(0, NOW + 1);
            assertSame(ash, ash.tick(FULL_HEALTH, false, NOW));
        }

        @Test
        void leftmostShortHeartRegrowsAHalfWhenItsIntervalPasses() {
            HeartOverlay regrown = kindledWithHalves(3, NOW).tick(FULL_HEALTH, false, NOW);
            assertEquals(2, regrown.shieldAt(1));
            assertEquals(0, regrown.shieldAt(2));
            assertEquals(NOW + HeartKind.KINDLE.regrowInterval(4), regrown.regrowAt());
        }

        @Test
        void noRegrowWhileWet() {
            HeartOverlay ash = kindledWithHalves(0, NOW);
            assertSame(ash, ash.tick(FULL_HEALTH, true, NOW));
        }

        @Test
        void regrowthReachesOnlyRealHearts() {
            assertEquals(1, kindledWithHalves(0, NOW).tick(0.5f, false, NOW).shieldAt(0));
            HeartOverlay full = kindledWithHalves(2, NOW);
            assertSame(full, full.tick(1f, false, NOW));
        }
    }

    @Nested
    class Burn {

        @Test
        void fireRelightsEveryHeartLeftForOneHeart() {
            HeartOverlay.Drained burned = kindledWithHalves(4, NOW).burn(1f, FULL_HEALTH, NOW);
            assertEquals(2f, burned.remainder(), DELTA);
            assertEquals(18, burned.overlay().shieldHalves());
            assertEquals(0, burned.overlay().shieldAt(9));
            assertEquals(NOW + HeartOverlay.FIRE_RELIGHT_COOLDOWN, burned.overlay().fireReadyAt());
        }

        @Test
        void fireInsideItsCooldownIsAnOrdinaryHit() {
            HeartOverlay relit = kindledWithHalves(4, NOW).burn(1f, FULL_HEALTH, NOW).overlay();
            HeartOverlay stripped = relit.drain(1f, NOW).overlay();
            HeartOverlay.Drained cooling = stripped.burn(1f, 18f, NOW + HeartOverlay.FIRE_RELIGHT_COOLDOWN - 1);
            assertEquals(0f, cooling.remainder(), DELTA);
            assertEquals(16, cooling.overlay().shieldHalves());
            HeartOverlay.Drained ready = stripped.burn(1f, 18f, NOW + HeartOverlay.FIRE_RELIGHT_COOLDOWN);
            assertEquals(16, ready.overlay().shieldHalves());
            assertEquals(2f, ready.remainder(), DELTA);
        }

        @Test
        void fireOnAnAllEmberBarIsAnOrdinaryHit() {
            HeartOverlay.Drained burned = kindled(FULL_HEALTH).burn(1f, FULL_HEALTH, NOW);
            assertEquals(19, burned.overlay().shieldHalves());
            assertEquals(NOW, burned.overlay().fireReadyAt());
        }

        @Test
        void allShieldedMeansNoRealHeartShortOfAFullShield() {
            assertTrue(kindledWithHalves(6, NOW).allShielded(6f));
            assertFalse(kindledWithHalves(5, NOW).allShielded(6f));
            assertFalse(kindledWithHalves(6, NOW).allShielded(7f));
        }
    }

    @Nested
    class HealInFire {

        @Test
        void regainedHeartsComeBackWholeEmber() {
            HeartOverlay lit = kindledWithHalves(4, NOW).healInFire(10f, 14f);
            assertEquals(2, lit.shieldAt(5));
            assertEquals(2, lit.shieldAt(6));
            assertEquals(0, lit.shieldAt(4));
        }

        @Test
        void healWithinAHeartLightsNothing() {
            HeartOverlay overlay = kindledWithHalves(4, NOW);
            assertSame(overlay, overlay.healInFire(9f, 10f));
        }
    }

    @Nested
    class Barkskin {

        @Test
        void ordinaryHitStripsBarkAndSparesHealth() {
            HeartOverlay.Drained drained = barked(FULL_HEALTH).drain(1f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(19, drained.overlay().shieldHalves());
        }

        @Test
        void hitPastEveryBarkCostsSingleAndEndsTheOverlay() {
            HeartOverlay.Drained drained = barked(4f).drain(6f, NOW);
            assertEquals(2f, drained.remainder(), DELTA);
            assertSame(HeartOverlay.NONE, drained.overlay());
        }

        @Test
        void aggravatedHitTakesItsWholeAmountAndBurnsItTwiceInHalves() {
            HeartOverlay.Drained drained = barked(FULL_HEALTH).aggravate(4f, NOW);
            assertEquals(4f, drained.remainder(), DELTA);
            assertEquals(12, drained.overlay().shieldHalves());
            assertEquals(2, drained.overlay().shieldAt(5));
            assertEquals(0, drained.overlay().shieldAt(6));
        }

        @Test
        void halfAHeartOfFireBurnsAWholeBark() {
            assertEquals(18, barked(FULL_HEALTH).aggravate(1f, NOW).overlay().shieldHalves());
        }

        @Test
        void tenFireAtAFullBarLeavesNoBark() {
            HeartOverlay.Drained drained = barked(FULL_HEALTH).aggravate(10f, NOW);
            assertEquals(10f, drained.remainder(), DELTA);
            assertSame(HeartOverlay.NONE, drained.overlay());
        }

        @Test
        void barkRegrowsAHalfEveryTwoAndAHalfSeconds() {
            HeartOverlay stripped = barked(FULL_HEALTH).drain(2f, NOW).overlay();
            assertEquals(NOW + 50L, stripped.regrowAt());
            assertSame(stripped, stripped.tick(FULL_HEALTH, false, NOW + 49L));
            assertEquals(19, stripped.tick(FULL_HEALTH, false, NOW + 50L).shieldHalves());
        }

        @Test
        void waterLeavesBarkStanding() {
            HeartOverlay stripped = barked(FULL_HEALTH).drain(1f, NOW).overlay();
            assertSame(stripped, stripped.tick(FULL_HEALTH, true, NOW + 1L));
        }
    }

    @Nested
    class RegrowInterval {

        @Test
        void kindleTakesASecondAHalfFromNoEmber() {
            assertEquals(20L, HeartKind.KINDLE.regrowInterval(0));
        }

        @Test
        void eachEmberHeartAddsAQuarterSecondAHalf() {
            assertEquals(25L, HeartKind.KINDLE.regrowInterval(2));
            assertEquals(65L, HeartKind.KINDLE.regrowInterval(18));
        }

        @Test
        void barkTakesTwoAndAHalfSecondsAHalf() {
            assertEquals(50L, HeartKind.BARKSKIN.regrowInterval(7));
        }
    }

    /**
     * Stoneskin: stone over the missing hearts, a physical hit reduced by the
     * brew's multiplier, a stone heart worth half a heart against explosions
     * and pickaxes, no regrowth, and healing held while stone stands
     * (decision stoneskin-stone-hearts-block-regeneration).
     */
    @Nested
    class Stoneskin {

        private static final float HALF_HEALTH = 10f;
        private static final float DAMAGE_TAKEN = 0.5f;
        private static final int MISSING_HALVES = 10;

        private HeartOverlay stoned() {
            return HeartOverlay.NONE.apply(HeartKind.STONESKIN, DURATION, HALF_HEALTH, FULL_HEALTH, DAMAGE_TAKEN,
                    NOW);
        }

        @Test
        void stoneFillsOnlyTheMissingHearts() {
            HeartOverlay overlay = stoned();
            assertEquals(MISSING_HALVES, overlay.shieldHalves());
            assertEquals(0, overlay.shieldAt(4));
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldAt(5));
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldAt(9));
        }

        @Test
        void physicalHitStripsTheReducedShare() {
            HeartOverlay.Drained drained = stoned().drainScaled(4f, DAMAGE_TAKEN, NOW);
            assertEquals(MISSING_HALVES - 2, drained.overlay().shieldHalves());
            assertEquals(0f, drained.remainder(), DELTA);
        }

        @Test
        void physicalHitPastTheStoneReachesHealthAtItsOwnScale() {
            HeartOverlay.Drained drained = stoned().drainScaled(30f, DAMAGE_TAKEN, NOW);
            assertFalse(drained.overlay().stands());
            assertEquals(10f, drained.remainder(), DELTA);
        }

        @Test
        void explosionFindsAStoneHeartWorthHalfAHeart() {
            HeartOverlay.Drained drained = stoned().drainScaled(2f, HeartOverlayEvents.STONE_BRITTLE_SHARE, NOW);
            assertEquals(MISSING_HALVES - 4, drained.overlay().shieldHalves());
            assertEquals(0f, drained.remainder(), DELTA);
        }

        @Test
        void stoneNeverRegrows() {
            HeartOverlay chipped = stoned().drainScaled(4f, DAMAGE_TAKEN, NOW).overlay();
            assertSame(chipped, chipped.tick(HALF_HEALTH, false, NOW + DURATION - 1));
            assertTrue(chipped.nextRegrowSlot(HALF_HEALTH).isEmpty());
        }

        @Test
        void healingIsHeldWhileStoneStandsAndFreedWhenItBreaks() {
            assertTrue(stoned().blocksHealing());
            assertFalse(stoned().drainScaled(30f, DAMAGE_TAKEN, NOW).overlay().blocksHealing());
            assertFalse(barked(FULL_HEALTH).blocksHealing());
        }

        @Test
        void drinkingAgainStacksTheDuration() {
            HeartOverlay twice = stoned().apply(HeartKind.STONESKIN, DURATION, HALF_HEALTH, FULL_HEALTH,
                    DAMAGE_TAKEN, NOW);
            assertEquals(NOW + 2L * DURATION, twice.expiresAt());
            assertEquals(MISSING_HALVES, twice.shieldHalves());
        }
    }
}
