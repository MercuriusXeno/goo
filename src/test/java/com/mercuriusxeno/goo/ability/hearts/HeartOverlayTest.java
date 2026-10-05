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

/** Kindle's overlay rules: laying embers, draining hits, quenching, reigniting and expiring (decisions overlay-hearts-are-an-elemental-overshield, kindle-ember-hearts-ash-and-retaliate). */
class HeartOverlayTest {

    private static final long NOW = 1_000L;
    private static final int DURATION = 1_200;
    private static final float FULL_HEALTH = 20f;
    private static final float DELTA = 1e-6f;

    private static HeartOverlay kindled(float health) {
        return HeartOverlay.NONE.apply(HeartKind.KINDLE, DURATION, health, NOW);
    }

    private static HeartOverlay withEmbers(int embers, int slots, long reigniteAt) {
        List<Boolean> flags = new ArrayList<>(Collections.nCopies(slots, Boolean.FALSE));
        for (int slot = 0; slot < embers; slot++) {
            flags.set(slot, Boolean.TRUE);
        }
        return new HeartOverlay(HeartKind.KINDLE, flags, NOW + DURATION, reigniteAt, NOW);
    }

    @Nested
    class Apply {

        @Test
        void everyPresentHeartTakesAnEmber() {
            HeartOverlay overlay = kindled(FULL_HEALTH);
            assertEquals(10, overlay.emberCount());
            assertEquals(NOW + DURATION, overlay.expiresAt());
        }

        @Test
        void missingHeartStaysMissing() {
            HeartOverlay overlay = kindled(13f);
            assertEquals(7, overlay.emberCount());
            assertFalse(overlay.emberAt(7));
        }

        @Test
        void sameKindAgainAddsDurationAndKeepsHearts() {
            HeartOverlay broken = kindled(FULL_HEALTH).drain(1f, NOW).overlay();
            HeartOverlay stacked = broken.apply(HeartKind.KINDLE, DURATION, FULL_HEALTH, NOW);
            assertEquals(NOW + 2L * DURATION, stacked.expiresAt());
            assertEquals(broken.embers(), stacked.embers());
        }
    }

    @Nested
    class Drain {

        @Test
        void hitBreaksRightmostEmberAndSparesHealth() {
            HeartOverlay.Drained drained = kindled(FULL_HEALTH).drain(1f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(9, drained.overlay().emberCount());
            assertFalse(drained.overlay().emberAt(9));
            assertTrue(drained.overlay().emberAt(8));
        }

        @Test
        void hitPastOneEmberWorthBreaksTheNext() {
            HeartOverlay.Drained drained = kindled(FULL_HEALTH).drain(3f, NOW);
            assertEquals(0f, drained.remainder(), DELTA);
            assertEquals(8, drained.overlay().emberCount());
        }

        @Test
        void hitPastEveryEmberCostsDouble() {
            HeartOverlay.Drained drained = withEmbers(1, 10, NOW).drain(3f, NOW);
            assertEquals(2f, drained.remainder(), DELTA);
            assertEquals(0, drained.overlay().emberCount());
        }

        @Test
        void hitOnBareAshCostsDouble() {
            HeartOverlay ash = withEmbers(0, 10, NOW);
            HeartOverlay.Drained drained = ash.drain(1.5f, NOW);
            assertEquals(3f, drained.remainder(), DELTA);
            assertSame(ash, drained.overlay());
        }

        @Test
        void noOverlayPassesTheHitWhole() {
            assertEquals(5f, HeartOverlay.NONE.drain(5f, NOW).remainder(), DELTA);
        }

        @Test
        void brokenEmberRestartsTheReigniteClock() {
            HeartOverlay drained = kindled(FULL_HEALTH).drain(1f, NOW + 5).overlay();
            assertEquals(NOW + 5 + HeartOverlay.reigniteInterval(9), drained.reigniteAt());
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
            assertEquals(0, quenched.emberCount());
            assertEquals(NOW + 1 + HeartOverlay.reigniteInterval(0), quenched.reigniteAt());
        }

        @Test
        void ashWaitsForItsInterval() {
            HeartOverlay ash = withEmbers(0, 10, NOW + 1);
            assertSame(ash, ash.tick(FULL_HEALTH, false, NOW));
        }

        @Test
        void leftmostAshReignitesWhenItsIntervalPasses() {
            HeartOverlay reignited = withEmbers(2, 10, NOW).tick(FULL_HEALTH, false, NOW);
            assertEquals(3, reignited.emberCount());
            assertTrue(reignited.emberAt(2));
            assertEquals(NOW + HeartOverlay.reigniteInterval(3), reignited.reigniteAt());
        }

        @Test
        void noReigniteWhileWet() {
            HeartOverlay ash = withEmbers(0, 10, NOW);
            assertSame(ash, ash.tick(FULL_HEALTH, true, NOW));
        }

        @Test
        void reigniteReachesOnlyRealHearts() {
            HeartOverlay ash = withEmbers(0, 10, NOW);
            HeartOverlay reignited = ash.tick(0.5f, false, NOW);
            assertTrue(reignited.emberAt(0));
            HeartOverlay full = withEmbers(1, 10, NOW);
            assertSame(full, full.tick(1f, false, NOW));
        }
    }

    @Nested
    class Burn {

        @Test
        void fireRelightsEveryHeartLeftForOneHeart() {
            HeartOverlay.Drained burned = withEmbers(2, 10, NOW).burn(1f, FULL_HEALTH, NOW);
            assertEquals(2f, burned.remainder(), DELTA);
            assertEquals(9, burned.overlay().emberCount());
            assertFalse(burned.overlay().emberAt(9));
            assertEquals(NOW + HeartOverlay.FIRE_REIGNITE_COOLDOWN, burned.overlay().igniteReadyAt());
        }

        @Test
        void fireInsideItsCooldownIsAnOrdinaryHit() {
            HeartOverlay relit = withEmbers(2, 10, NOW).burn(1f, FULL_HEALTH, NOW).overlay();
            HeartOverlay broken = relit.drain(1f, NOW).overlay();
            HeartOverlay.Drained cooling = broken.burn(1f, 18f, NOW + HeartOverlay.FIRE_REIGNITE_COOLDOWN - 1);
            assertEquals(0f, cooling.remainder(), DELTA);
            assertEquals(7, cooling.overlay().emberCount());
            HeartOverlay.Drained ready = broken.burn(1f, 18f, NOW + HeartOverlay.FIRE_REIGNITE_COOLDOWN);
            assertEquals(8, ready.overlay().emberCount());
        }

        @Test
        void fireOnAnAllEmberBarIsAnOrdinaryHit() {
            HeartOverlay.Drained burned = kindled(FULL_HEALTH).burn(1f, FULL_HEALTH, NOW);
            assertEquals(9, burned.overlay().emberCount());
            assertEquals(NOW, burned.overlay().igniteReadyAt());
        }

        @Test
        void allEmberMeansNoRealHeartIsAsh() {
            assertTrue(withEmbers(3, 10, NOW).allEmber(6f));
            assertFalse(withEmbers(3, 10, NOW).allEmber(7f));
        }
    }

    @Nested
    class HealInFire {

        @Test
        void regainedHeartsComeBackEmber() {
            HeartOverlay lit = withEmbers(2, 5, NOW).healInFire(10f, 14f);
            assertTrue(lit.emberAt(5));
            assertTrue(lit.emberAt(6));
            assertFalse(lit.emberAt(4));
        }

        @Test
        void healWithinAHeartLightsNothing() {
            HeartOverlay overlay = withEmbers(2, 5, NOW);
            assertSame(overlay, overlay.healInFire(9f, 10f));
        }
    }

    @Nested
    class ReigniteInterval {

        @Test
        void twoSecondsFromNoEmber() {
            assertEquals(40L, HeartOverlay.reigniteInterval(0));
        }

        @Test
        void eachEmberStandingAddsHalfASecond() {
            assertEquals(70L, HeartOverlay.reigniteInterval(3));
            assertEquals(130L, HeartOverlay.reigniteInterval(9));
        }
    }
}
