package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers whom a rallied mob fights: the player who attacked the caster while
 * alive in reach, else the mob that last hurt the caster, else the nearest
 * one targeting the caster, else the one the caster last struck, and never a
 * mob that is none of these
 * (decisions zoo-rally-arms-the-peaceful, rallied-mobs-turn-on-a-player-who-attacks-their-rallier).
 */
class RallyFoesTest {

    private static final UUID CASTER = new UUID(0, 1);
    private static final UUID HURTER = new UUID(0, 2);
    private static final UUID STALKER = new UUID(0, 3);
    private static final UUID STRUCK = new UUID(0, 4);
    private static final UUID BYSTANDER = new UUID(0, 5);
    private static final UUID ATTACKER = new UUID(0, 7);

    private static final UUID HOSTILE = new UUID(0, 8);

    private static RallyFoes.Candidate<String> near(String name, UUID id, UUID target, double distanceSqr) {
        return new RallyFoes.Candidate<>(name, id, true, false, target, distanceSqr);
    }

    private static RallyFoes.Candidate<String> hostile(String name, UUID id, double distanceSqr) {
        return new RallyFoes.Candidate<>(name, id, true, true, null, distanceSqr);
    }

    /** rallied-mobs-follow-the-player-who-rallied-them: a hostile near the rallier is a foe though it targets no one. */
    @Test
    void aHostileInReachIsAFoeWhenNoHigherFoeStands() {
        assertEquals(Optional.of("near hostile"), RallyFoes.pick(CASTER, null, null, null,
                List.of(near("bystander", BYSTANDER, null, 1), hostile("far hostile", new UUID(0, 9), 16),
                        hostile("near hostile", HOSTILE, 4))));
    }

    /** rallied-mobs-follow-the-player-who-rallied-them */
    @Test
    void theMobTheCasterStruckComesBeforeANearerHostile() {
        assertEquals(Optional.of("struck"), RallyFoes.pick(CASTER, null, null, STRUCK,
                List.of(hostile("hostile", HOSTILE, 1), near("struck", STRUCK, null, 16))));
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Test
    void thePlayerWhoAttackedTheCasterComesBeforeANearerHostile() {
        List<RallyFoes.Candidate<String>> nearby = List.of(near("stalker", STALKER, CASTER, 1),
                near("hurter", HURTER, null, 1), near("attacker", ATTACKER, null, 16));

        assertEquals(Optional.of("attacker"), RallyFoes.pick(CASTER, ATTACKER, HURTER, STRUCK, nearby));
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Test
    void theAttackerStandsWhileAliveInReach() {
        assertTrue(RallyFoes.attackerStands(ATTACKER, List.of(near("attacker", ATTACKER, null, 16))));
    }

    /** rallied-mobs-turn-on-a-player-who-attacks-their-rallier */
    @Test
    void theAttackerFallsWhenDeadOrOutOfReach() {
        assertFalse(RallyFoes.attackerStands(ATTACKER,
                List.of(new RallyFoes.Candidate<>("dead attacker", ATTACKER, false, false, null, 1))));
        assertFalse(RallyFoes.attackerStands(ATTACKER, List.of(near("bystander", BYSTANDER, null, 1))));
    }

    @Test
    void theMobThatHurtTheCasterComesFirst() {
        List<RallyFoes.Candidate<String>> nearby = List.of(near("stalker", STALKER, CASTER, 1),
                near("hurter", HURTER, null, 9), near("struck", STRUCK, null, 1));

        assertEquals(Optional.of("hurter"), RallyFoes.pick(CASTER, null, HURTER, STRUCK, nearby));
    }

    @Test
    void aMobTargetingTheCasterComesBeforeTheOneItStruck() {
        List<RallyFoes.Candidate<String>> nearby = List.of(near("struck", STRUCK, null, 1),
                near("far stalker", STALKER, CASTER, 16), near("near stalker", new UUID(0, 6), CASTER, 4));

        assertEquals(Optional.of("near stalker"), RallyFoes.pick(CASTER, null, null, STRUCK, nearby));
    }

    @Test
    void theMobTheCasterStruckIsTheLastFoe() {
        assertEquals(Optional.of("struck"), RallyFoes.pick(CASTER, null, null, STRUCK,
                List.of(near("bystander", BYSTANDER, null, 1), near("struck", STRUCK, null, 4))));
    }

    @Test
    void aBystanderIsNoFoe() {
        assertEquals(Optional.empty(), RallyFoes.pick(CASTER, null, HURTER, STRUCK,
                List.of(near("bystander", BYSTANDER, null, 1),
                        new RallyFoes.Candidate<>("dead hurter", HURTER, false, false, null, 1))));
    }
}
