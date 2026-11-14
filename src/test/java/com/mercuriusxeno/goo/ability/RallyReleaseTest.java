package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.zoo.Rallied;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which casts release the caster's own rallied mob for free: a rallying
 * program at a mob the caster rallied, and no other
 * (decision rally-release-cast-costs-nothing).
 */
class RallyReleaseTest {

    private static final UUID CASTER = new UUID(0L, 1L);
    private static final UUID OTHER = new UUID(0L, 2L);
    private static final float RALLY_DAMAGE = 6f;

    @Test
    void rallyNestedInItsTargetBehaviorReleasesTheCastersOwnRally() {
        assertTrue(RallyRelease.releasesOwnRally(AbilityJson.decode("zoo_rally").behaviors(), CASTER,
                new Rallied(CASTER, RALLY_DAMAGE)));
    }

    @Test
    void rallyAtAMobAnotherPlayerRalliedReleasesNothing() {
        assertFalse(RallyRelease.releasesOwnRally(AbilityJson.decode("zoo_rally").behaviors(), CASTER,
                new Rallied(OTHER, RALLY_DAMAGE)));
    }

    @Test
    void anAbilityThatDoesNotRallyReleasesNothing() {
        assertFalse(RallyRelease.releasesOwnRally(AbilityJson.decode("unstable_lurker").behaviors(), CASTER,
                new Rallied(CASTER, RALLY_DAMAGE)));
    }
}
