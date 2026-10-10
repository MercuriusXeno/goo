package com.mercuriusxeno.goo.ability.hex;

import com.mercuriusxeno.goo.ability.hex.CharmTargets.Candidate;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the foe a charmed mob turns on: of the living mobs near it, the
 * nearest targeting its charmer, else the nearest hostile one.
 */
class CharmTargetsTest {

    private static final UUID CHARMER = new UUID(1L, 1L);
    private static final UUID STRANGER = new UUID(2L, 2L);
    private static final double NEAR = 4.0;
    private static final double FAR = 64.0;

    @Test
    void picksTheNearestHostileBeforeItAggresses() {
        List<Candidate<String>> nearby = List.of(new Candidate<>("far", true, true, null, FAR),
                new Candidate<>("near", true, true, null, NEAR));

        assertEquals(Optional.of("near"), CharmTargets.nearestFoe(CHARMER, nearby));
    }

    @Test
    void picksAMobTargetingTheCharmerOverANearerHostile() {
        List<Candidate<String>> nearby = List.of(new Candidate<>("idle hostile", true, true, null, NEAR),
                new Candidate<>("aggressor", true, false, CHARMER, FAR));

        assertEquals(Optional.of("aggressor"), CharmTargets.nearestFoe(CHARMER, nearby));
    }

    @Test
    void sparesNeutralsTargetingSomeoneElse() {
        List<Candidate<String>> nearby = List.of(new Candidate<>("neutral", true, false, STRANGER, NEAR));

        assertEquals(Optional.empty(), CharmTargets.nearestFoe(CHARMER, nearby));
    }

    @Test
    void passesOverTheDead() {
        List<Candidate<String>> nearby = List.of(new Candidate<>("dead aggressor", false, true, CHARMER, NEAR));

        assertEquals(Optional.empty(), CharmTargets.nearestFoe(CHARMER, nearby));
    }
}
