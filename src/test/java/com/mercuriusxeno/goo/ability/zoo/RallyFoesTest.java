package com.mercuriusxeno.goo.ability.zoo;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers whom a rallied mob fights: the mob that last hurt the caster, else
 * the nearest one targeting the caster, else the one the caster last struck,
 * and never a mob that is none of these
 * (decision zoo-rally-arms-the-peaceful).
 */
class RallyFoesTest {

    private static final UUID CASTER = new UUID(0, 1);
    private static final UUID HURTER = new UUID(0, 2);
    private static final UUID STALKER = new UUID(0, 3);
    private static final UUID STRUCK = new UUID(0, 4);
    private static final UUID BYSTANDER = new UUID(0, 5);

    private static RallyFoes.Candidate<String> near(String name, UUID id, UUID target, double distanceSqr) {
        return new RallyFoes.Candidate<>(name, id, true, target, distanceSqr);
    }

    @Test
    void theMobThatHurtTheCasterComesFirst() {
        List<RallyFoes.Candidate<String>> nearby = List.of(near("stalker", STALKER, CASTER, 1),
                near("hurter", HURTER, null, 9), near("struck", STRUCK, null, 1));

        assertEquals(Optional.of("hurter"), RallyFoes.pick(CASTER, HURTER, STRUCK, nearby));
    }

    @Test
    void aMobTargetingTheCasterComesBeforeTheOneItStruck() {
        List<RallyFoes.Candidate<String>> nearby = List.of(near("struck", STRUCK, null, 1),
                near("far stalker", STALKER, CASTER, 16), near("near stalker", new UUID(0, 6), CASTER, 4));

        assertEquals(Optional.of("near stalker"), RallyFoes.pick(CASTER, null, STRUCK, nearby));
    }

    @Test
    void theMobTheCasterStruckIsTheLastFoe() {
        assertEquals(Optional.of("struck"), RallyFoes.pick(CASTER, null, STRUCK,
                List.of(near("bystander", BYSTANDER, null, 1), near("struck", STRUCK, null, 4))));
    }

    @Test
    void aBystanderIsNoFoe() {
        assertEquals(Optional.empty(), RallyFoes.pick(CASTER, HURTER, STRUCK,
                List.of(near("bystander", BYSTANDER, null, 1),
                        new RallyFoes.Candidate<>("dead hurter", HURTER, false, null, 1))));
    }
}
