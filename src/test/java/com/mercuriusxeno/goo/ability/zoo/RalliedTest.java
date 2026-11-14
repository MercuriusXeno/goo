package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers whom a mob follows when a rally reaches it, the attacker a second
 * rally keeps, whose hit ends the rally, and whose cast releases it.
 */
class RalliedTest {

    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);
    private static final UUID ATTACKER = new UUID(0L, 3L);
    private static final float DAMAGE = 6f;

    @Nested
    class Rallier {

        @Test
        void unralliedMobTakesTheCaller() {
            assertEquals(SECOND, Rallied.rallierAfter(null, SECOND));
        }

        /** rally-remembers-the-first-rallier */
        @Test
        void ralliedMobKeepsItsFirstRallier() {
            assertEquals(FIRST, Rallied.rallierAfter(new Rallied(FIRST, DAMAGE), SECOND));
        }
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Nested
    class Attacker {

        @Test
        void aSecondRallyKeepsTheStandingAttacker() {
            Rallied attacked = new Rallied(FIRST, DAMAGE).withAttacker(ATTACKER);

            assertEquals(Optional.of(ATTACKER), Rallied.attackerAfter(attacked));
        }

        @Test
        void aFirstRallyRecordsNoAttacker() {
            assertEquals(Optional.empty(), Rallied.attackerAfter(null));
        }
    }

    /** rally-is-a-mob-effect-on-one-target: the rally fades when its rallier strikes the mob, as Charm does. */
    @Nested
    class BrokenBy {

        @Test
        void theRalliersHitBreaksTheRally() {
            assertTrue(new Rallied(FIRST, DAMAGE).brokenBy(FIRST));
        }

        @Test
        void anotherAttackersHitLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).brokenBy(SECOND));
        }

        @Test
        void damageWithNoAttackerLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).brokenBy(null));
        }
    }

    /** rally-cast-on-your-rallied-mob-releases-it */
    @Nested
    class ReleasedBy {

        @Test
        void theRalliersCastReleasesTheRally() {
            assertTrue(new Rallied(FIRST, DAMAGE).releasedBy(FIRST));
        }

        @Test
        void anotherCastersCastLeavesTheRally() {
            assertFalse(new Rallied(FIRST, DAMAGE).releasedBy(SECOND));
        }

        @Test
        void aMobHoldingNoRallyHasNoRallyToRelease() {
            // NONE's own caster UUID asked, so only the no-rally rule answers false
            assertFalse(Rallied.NONE.releasedBy(Rallied.NONE.caster()));
        }
    }
}
