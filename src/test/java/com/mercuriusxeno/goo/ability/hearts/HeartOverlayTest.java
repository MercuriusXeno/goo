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

    /** A whole bar of the kind, every present heart fully shielded, as the crawl leaves it. */
    private static HeartOverlay laid(HeartKind kind, float health) {
        int filled = HeartOverlay.filledSlots(health);
        return new HeartOverlay(kind, Collections.nCopies(filled, HeartOverlay.FULL_SHIELD), NOW + DURATION,
                NOW + kind.regrowInterval(filled * HeartOverlay.FULL_SHIELD), NOW);
    }

    /** Ticks an overlay once a game tick from now through the last tick, at a steady health. */
    private static HeartOverlay tickedThrough(HeartOverlay start, float health, long last) {
        HeartOverlay overlay = start;
        for (long tick = NOW; tick <= last; tick++) {
            overlay = overlay.tick(health, false, tick);
        }
        return overlay;
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

    /** Reserve drains health while held into reserve hearts behind the bar, by the JSON's ratio, cap and floor, and never expires (decision reserve-hearts-sit-behind-the-bar). */
    @Nested
    class Reserve {

        /** vital_reserve.json: half a heart drained every ten ticks, two hearts banking one, ten hearts at most, never under half a heart. */
        private static final ReserveDrain VITAL_RESERVE = new ReserveDrain(0.05f, 0.5f, 10f, 0.5f);
        private static final float SUM_DELTA = 1e-4f;
        private static final int CAP_HALVES = 20;

        private ReserveDrain.Drawn held(HeartOverlay standing, float health, int ticks) {
            ReserveDrain.Drawn drawn = new ReserveDrain.Drawn(health, standing);
            for (int tick = 0; tick < ticks; tick++) {
                drawn = VITAL_RESERVE.draw(drawn.overlay(), drawn.health());
            }
            return drawn;
        }

        private HeartOverlay reserveOf(Integer... halves) {
            return new HeartOverlay(HeartKind.RESERVE, List.of(halves), HeartOverlay.NEVER_EXPIRES, 0L, 0L);
        }

        @Test
        void eachHeldTickDrainsTheJsonAmount() {
            ReserveDrain.Drawn drawn = held(HeartOverlay.NONE, FULL_HEALTH, 1);
            assertEquals(19.9f, drawn.health(), SUM_DELTA);
            assertTrue(drawn.overlay().reserves());
            assertEquals(0, drawn.overlay().shieldHalves());
        }

        @Test
        void twoHeartsDrainedBankOneReserveHeart() {
            ReserveDrain.Drawn drawn = held(HeartOverlay.NONE, FULL_HEALTH, 40);
            assertEquals(16f, drawn.health(), SUM_DELTA);
            assertEquals(List.of(HeartOverlay.FULL_SHIELD), drawn.overlay().shields());
        }

        @Test
        void oneHeartDrainedBanksHalfAHeart() {
            assertEquals(List.of(1), held(HeartOverlay.NONE, FULL_HEALTH, 20).overlay().shields());
        }

        @Test
        void aBankedHalfFillsTheLeftmostShortSlot() {
            HeartOverlay banked = reserveOf(2, 1, 0).bank(2f, 0.5f, CAP_HALVES);
            assertEquals(List.of(2, 2, 0), banked.shields());
        }

        @Test
        void theDrainStopsAtTheFloor() {
            ReserveDrain.Drawn drawn = held(HeartOverlay.NONE, 1.05f, 5);
            assertEquals(1f, drawn.health(), SUM_DELTA);
            ReserveDrain.Drawn atFloor = VITAL_RESERVE.draw(drawn.overlay(), drawn.health());
            assertSame(drawn.overlay(), atFloor.overlay());
            assertEquals(1f, atFloor.health(), SUM_DELTA);
        }

        @Test
        void aReserveAtTheCapTakesNoMoreHealth() {
            HeartOverlay capped = reserveOf(Collections.nCopies(10, HeartOverlay.FULL_SHIELD).toArray(Integer[]::new));
            ReserveDrain.Drawn drawn = VITAL_RESERVE.draw(capped, FULL_HEALTH);
            assertSame(capped, drawn.overlay());
            assertEquals(FULL_HEALTH, drawn.health(), DELTA);
        }

        @Test
        void bankingStopsAtTheCapAndDropsTheCarry() {
            HeartOverlay banked = reserveOf(2, 2, 2, 2, 2, 2, 2, 2, 2, 1).bank(10f, 0.5f, CAP_HALVES);
            assertEquals(CAP_HALVES, banked.shieldHalves());
            assertEquals(0f, banked.drainCarry(), DELTA);
        }

        @Test
        void drainingEndsAnotherHeartBrew() {
            assertEquals(HeartKind.RESERVE, held(kindled(FULL_HEALTH), FULL_HEALTH, 1).overlay().kind());
        }

        @Test
        void theReserveNeverExpires() {
            HeartOverlay reserve = reserveOf(2, 2);
            assertSame(reserve, reserve.tick(FULL_HEALTH, false, Long.MAX_VALUE - 1));
        }

        @Test
        void hitsSpendTheReserveAtAFullHeartEachBeforeHealth() {
            HeartOverlay.Drained drained = reserveOf(2, 2).drain(3f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(1, drained.overlay().shieldHalves());
        }

        @Test
        void aHitPastTheReserveEndsItAndTheRestReachesHealth() {
            HeartOverlay.Drained drained = reserveOf(2, 2).drain(6f, NOW);
            assertEquals(2f, drained.remainder(), DELTA);
            assertFalse(drained.overlay().stands());
        }

        @Test
        void aSpentReserveNeverRegrows() {
            HeartOverlay spent = reserveOf(2, 2).drain(3f, NOW).overlay();
            assertSame(spent, spent.tick(FULL_HEALTH, false, NOW + DURATION));
            assertTrue(spent.nextRegrowSlot(FULL_HEALTH).isEmpty());
        }

        @Test
        void waterLeavesTheReserveStanding() {
            HeartOverlay reserve = reserveOf(2, 2);
            assertSame(reserve, reserve.tick(FULL_HEALTH, true, NOW + 1));
        }
    }

    @Nested
    class Apply {

        @Test
        void kindleAshesEveryHeartAndEmbersOne() {
            // heart-effects-crawl-while-held
            HeartOverlay overlay = HeartOverlay.NONE.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW);
            List<Integer> ashBehindOne = new ArrayList<>(Collections.nCopies(10, 0));
            ashBehindOne.set(0, HeartOverlay.FULL_SHIELD);
            assertEquals(ashBehindOne, overlay.shields());
            assertEquals(NOW + DURATION, overlay.expiresAt());
        }

        @Test
        void kindleReignitesTheNextHeartPastItsIntervalUntilAllAreEmber() {
            HeartOverlay overlay = HeartOverlay.NONE.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW);
            HeartOverlay oneHalfOn = tickedThrough(overlay, FULL_HEALTH, overlay.regrowAt());
            assertEquals(1, oneHalfOn.shieldAt(1));
            assertEquals(0, oneHalfOn.shieldAt(2));
            assertEquals(FULL_HALVES, tickedThrough(overlay, FULL_HEALTH, NOW + DURATION - 1).shieldHalves());
        }

        @Test
        void missingHeartStaysMissing() {
            HeartOverlay overlay = HeartOverlay.NONE.apply(HeartKind.KINDLE, DURATION, 13f, NOW);
            assertEquals(7, overlay.shields().size());
            assertEquals(14, tickedThrough(overlay, 13f, NOW + DURATION - 1).shieldHalves());
            assertEquals(0, overlay.shieldAt(7));
        }

        @Test
        void sameKindAgainAddsNoDuration() {
            HeartOverlay broken = kindled(FULL_HEALTH).drain(1f, NOW).overlay();
            assertSame(broken, broken.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW));
        }

        @Test
        void heldStartNeverExpires() {
            // self-effects-trickle-until-ended
            HeartOverlay held = HeartOverlay.NONE.hold(HeartKind.KINDLE, FULL_HEALTH, FULL_HEALTH,
                    HeartOverlay.WHOLE_HIT, NOW);
            assertEquals(HeartOverlay.NEVER_EXPIRES, held.expiresAt());
            assertEquals(HeartOverlay.FULL_SHIELD, held.shieldHalves());
            assertEquals(FULL_HALVES, tickedThrough(held, FULL_HEALTH, NOW + 100L * DURATION).shieldHalves());
        }

        @Test
        void sameKindHeldAgainLeavesItUnchanged() {
            HeartOverlay held = HeartOverlay.NONE.hold(HeartKind.KINDLE, FULL_HEALTH, FULL_HEALTH,
                    HeartOverlay.WHOLE_HIT, NOW).drain(1f, NOW).overlay();
            assertSame(held, held.hold(HeartKind.KINDLE, FULL_HEALTH, FULL_HEALTH, HeartOverlay.WHOLE_HIT, NOW + 1));
            assertSame(held, held.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW + 1));
        }

        @Test
        void anotherKindReplacesTheStandingOverlayFromOneHeart() {
            HeartOverlay spent = kindled(FULL_HEALTH).drain(5f, NOW).overlay()
                    .burn(1f, FULL_HEALTH, NOW + 1).overlay();
            HeartOverlay barked = spent.apply(HeartKind.BARKSKIN, DURATION, 18f, NOW + 2);
            assertEquals(HeartKind.BARKSKIN, barked.kind());
            List<Integer> oneBark = new ArrayList<>(Collections.nCopies(9, 0));
            oneBark.set(0, HeartOverlay.FULL_SHIELD);
            assertEquals(oneBark, barked.shields());
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
        void hitPastEveryBarkCostsSingleAndLeavesBarkskinStanding() {
            // heart-effects-crawl-while-held: a held effect stays open with no bark, its crawl regrowing it
            HeartOverlay.Drained drained = barked(4f).drain(6f, NOW);
            assertEquals(2f, drained.remainder(), DELTA);
            assertTrue(drained.overlay().stands());
            assertEquals(0, drained.overlay().shieldHalves());
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
        void tenFireAtAFullBarLeavesNoBarkAndBarkskinRegrowing() {
            HeartOverlay.Drained drained = barked(FULL_HEALTH).aggravate(10f, NOW);
            assertEquals(10f, drained.remainder(), DELTA);
            assertEquals(0, drained.overlay().shieldHalves());
            assertEquals(0, drained.overlay().nextRegrowSlot(10f).orElseThrow());
        }

        @Test
        void barkRegrowsAHalfEveryTwoAndAHalfSeconds() {
            HeartOverlay stripped = barked(FULL_HEALTH).drain(2f, NOW).overlay();
            assertEquals(NOW + 50L, stripped.regrowAt());
            assertSame(stripped, stripped.tick(FULL_HEALTH, false, NOW + 49L));
            assertEquals(19, stripped.tick(FULL_HEALTH, false, NOW + 50L).shieldHalves());
        }

        /** Growth's hold runs the bark's regrow clock ahead (decision growth-breeze-ticks-plants). */
        @Test
        void growthHeldThreeTimesAsFastRegrowsBarkInAThirdOfTheTime() {
            HeartOverlay overlay = barked(FULL_HEALTH).drain(2f, NOW).overlay();
            long tick = NOW;
            while (overlay.shieldHalves() < 19) {
                tick++;
                overlay = overlay.hastened(2).tick(FULL_HEALTH, false, tick);
            }
            assertEquals(NOW + 17L, tick);
        }

        @Test
        void barkskinStartsWithOneBarkHeartAndBarksEachFurtherHeart() {
            // heart-effects-crawl-while-held
            HeartOverlay overlay = HeartOverlay.NONE.apply(HeartKind.BARKSKIN, DURATION, FULL_HEALTH, NOW);
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldAt(0));
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldHalves());
            long interval = HeartKind.BARKSKIN.regrowInterval(0);
            assertEquals(HeartOverlay.FULL_SHIELD, tickedThrough(overlay, FULL_HEALTH, NOW + interval - 1).shieldHalves());
            assertEquals(1, tickedThrough(overlay, FULL_HEALTH, NOW + interval).shieldAt(1));
            assertEquals(2, tickedThrough(overlay, FULL_HEALTH, NOW + 2 * interval).shieldAt(1));
            assertEquals(FULL_HALVES, tickedThrough(overlay, FULL_HEALTH, NOW + 18 * interval).shieldHalves());
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

        private static final float SIX_HEALTH = 6f;
        /** The first slot six health leaves missing. */
        private static final int FIRST_MISSING_AT_SIX = 3;

        /** Stone over every missing heart, as the crawl leaves it. */
        private HeartOverlay stoned() {
            List<Integer> stone = new ArrayList<>(Collections.nCopies(5, 0));
            stone.addAll(Collections.nCopies(5, HeartOverlay.FULL_SHIELD));
            return new HeartOverlay(HeartKind.STONESKIN, stone, NOW + DURATION,
                    NOW + HeartKind.STONESKIN.regrowInterval(MISSING_HALVES), NOW, DAMAGE_TAKEN, 0f);
        }

        private HeartOverlay freshAtSix() {
            return HeartOverlay.NONE.apply(HeartKind.STONESKIN, DURATION, SIX_HEALTH, FULL_HEALTH, DAMAGE_TAKEN, NOW);
        }

        @Test
        void stoneStartsOnTheLeftmostMissingHeart() {
            // heart-effects-crawl-while-held
            HeartOverlay overlay = freshAtSix();
            assertEquals(10, overlay.shields().size());
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldHalves());
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldAt(FIRST_MISSING_AT_SIX));
            assertTrue(overlay.blocksHealing());
        }

        @Test
        void stoneFillsTheEmptyHalfOfAHalfHeartFirst() {
            // heart-effects-crawl-while-held: five health leaves heart three half full
            HeartOverlay overlay = HeartOverlay.NONE.apply(HeartKind.STONESKIN, DURATION, 5f, FULL_HEALTH,
                    DAMAGE_TAKEN, NOW);
            assertEquals(1, overlay.shieldAt(2));
            assertEquals(1, overlay.shieldHalves());
            long interval = HeartKind.STONESKIN.regrowInterval(0);
            HeartOverlay crept = tickedThrough(overlay, 5f, NOW + interval);
            assertEquals(1, crept.shieldAt(3));
            assertEquals(15, tickedThrough(overlay, 5f, NOW + 16 * interval).shieldHalves());
        }

        @Test
        void aHalfHeartWoundIsStonedByHalf() {
            long interval = HeartKind.STONESKIN.regrowInterval(0);
            HeartOverlay whole = tickedThrough(freshAtSix(), SIX_HEALTH, NOW + 14 * interval);
            HeartOverlay overlay = whole;
            for (long tick = NOW + 14 * interval + 1; tick <= NOW + 15 * interval; tick++) {
                overlay = overlay.tick(5f, false, tick);
            }
            assertEquals(1, overlay.shieldAt(FIRST_MISSING_AT_SIX - 1));
            assertEquals(1, overlay.crawlHalf(FIRST_MISSING_AT_SIX - 1, 5f));
            assertEquals(15, overlay.shieldHalves());
        }

        @Test
        void stoneCrawlsIntoEachFurtherMissingHeart() {
            HeartOverlay overlay = freshAtSix();
            long interval = HeartKind.STONESKIN.regrowInterval(0);
            HeartOverlay crept = tickedThrough(overlay, SIX_HEALTH, NOW + interval);
            assertEquals(1, crept.shieldAt(FIRST_MISSING_AT_SIX + 1));
            assertTrue(crept.blocksHealing());
            HeartOverlay whole = tickedThrough(overlay, SIX_HEALTH, NOW + 14 * interval);
            assertEquals(14, whole.shieldHalves());
            assertEquals(0, whole.shieldAt(FIRST_MISSING_AT_SIX - 1));
            assertTrue(whole.blocksHealing());
        }

        @Test
        void aWoundOpenedLaterIsStonedToo() {
            long interval = HeartKind.STONESKIN.regrowInterval(0);
            HeartOverlay whole = tickedThrough(freshAtSix(), SIX_HEALTH, NOW + 14 * interval);
            float wounded = 4f;
            HeartOverlay overlay = whole;
            for (long tick = NOW + 14 * interval + 1; tick <= NOW + 16 * interval; tick++) {
                overlay = overlay.tick(wounded, false, tick);
                assertTrue(overlay.blocksHealing());
            }
            assertEquals(HeartOverlay.FULL_SHIELD, overlay.shieldAt(FIRST_MISSING_AT_SIX - 1));
            assertEquals(16, overlay.shieldHalves());
        }

        @Test
        void stoneFillsOnlyTheMissingHearts() {
            HeartOverlay whole = tickedThrough(HeartOverlay.NONE.apply(HeartKind.STONESKIN, DURATION, HALF_HEALTH,
                    FULL_HEALTH, DAMAGE_TAKEN, NOW), HALF_HEALTH, NOW + DURATION - 1);
            assertEquals(MISSING_HALVES, whole.shieldHalves());
            assertEquals(0, whole.shieldAt(4));
            assertEquals(HeartOverlay.FULL_SHIELD, whole.shieldAt(5));
            assertEquals(HeartOverlay.FULL_SHIELD, whole.shieldAt(9));
        }

        @Test
        void physicalHitStripsTheReducedShare() {
            HeartOverlay.Drained drained = stoned().drainScaled(4f, DAMAGE_TAKEN, NOW);
            assertEquals(MISSING_HALVES - 2, drained.overlay().shieldHalves());
            assertEquals(0f, drained.remainder(), DELTA);
        }

        @Test
        void physicalHitPastTheStoneReachesHealthAtItsOwnScaleAndStoneskinStands() {
            HeartOverlay.Drained drained = stoned().drainScaled(30f, DAMAGE_TAKEN, NOW);
            assertTrue(drained.overlay().stands());
            assertEquals(0, drained.overlay().shieldHalves());
            assertEquals(10f, drained.remainder(), DELTA);
        }

        @Test
        void explosionFindsAStoneHeartWorthHalfAHeart() {
            HeartOverlay.Drained drained = stoned().drainScaled(2f, HeartOverlayEvents.STONE_BRITTLE_SHARE, NOW);
            assertEquals(MISSING_HALVES - 4, drained.overlay().shieldHalves());
            assertEquals(0f, drained.remainder(), DELTA);
        }

        @Test
        void brokenStoneCrawlsBack() {
            HeartOverlay chipped = stoned().drainScaled(4f, DAMAGE_TAKEN, NOW).overlay();
            assertEquals(9, chipped.nextRegrowSlot(HALF_HEALTH).orElseThrow());
            assertEquals(MISSING_HALVES, tickedThrough(chipped, HALF_HEALTH, NOW + DURATION - 1).shieldHalves());
        }

        @Test
        void healingIsHeldWhileStoneskinStandsEvenWithItsStoneBroken() {
            assertTrue(stoned().blocksHealing());
            assertTrue(stoned().drainScaled(30f, DAMAGE_TAKEN, NOW).overlay().blocksHealing());
            assertFalse(barked(FULL_HEALTH).blocksHealing());
        }

        @Test
        void drinkingAgainAddsNoDuration() {
            HeartOverlay stone = stoned();
            assertSame(stone, stone.apply(HeartKind.STONESKIN, DURATION, HALF_HEALTH, FULL_HEALTH, DAMAGE_TAKEN, NOW));
        }
    }
}
