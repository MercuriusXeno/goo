package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers whom a mob follows when a rally reaches it: the first rallier
 * while that rally stands, the caller once it has ended; and the attacker
 * the rally records, kept while the rally stands and cleared once it ends.
 */
class RalliedTest {

    private static final UUID FIRST = new UUID(0L, 1L);
    private static final UUID SECOND = new UUID(0L, 2L);
    private static final UUID ATTACKER = new UUID(0L, 3L);
    private static final long NOW = 1000L;
    private static final float DAMAGE = 6f;

    @Test
    void unralliedMobTakesTheCaller() {
        assertEquals(SECOND, Rallied.rallierAfter(null, SECOND, NOW));
    }

    @Test
    void mobUnderAStandingRallyKeepsItsFirstRallier() {
        assertEquals(FIRST, Rallied.rallierAfter(new Rallied(FIRST, NOW + 1, DAMAGE), SECOND, NOW));
    }

    @Test
    void mobWhoseRallyEndedTakesTheCaller() {
        assertEquals(SECOND, Rallied.rallierAfter(new Rallied(FIRST, NOW, DAMAGE), SECOND, NOW));
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Test
    void aStandingRallyKeepsItsAttackerThroughARefresh() {
        Rallied attacked = new Rallied(FIRST, NOW + 1, DAMAGE).withAttacker(ATTACKER);

        assertEquals(Optional.of(ATTACKER), Rallied.attackerAfter(attacked, NOW));
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Test
    void theAttackerRecordClearsWhenTheRallyEnds() {
        Rallied attacked = new Rallied(FIRST, NOW, DAMAGE).withAttacker(ATTACKER);

        assertEquals(Optional.empty(), Rallied.attackerAfter(attacked, NOW));
    }
}
