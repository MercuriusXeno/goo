package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.yarn.Led;
import org.junit.jupiter.api.Test;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers which casts release the caster's own led mob for free: a leading
 * program at a mob the caster led, and no other
 * (decision rally-release-cast-costs-nothing).
 */
class LeadReleaseTest {

    private static final UUID CASTER = new UUID(0L, 1L);
    private static final UUID OTHER = new UUID(0L, 2L);
    private static final float LEAD_DAMAGE = 6f;

    @Test
    void leadNestedInItsTargetBehaviorReleasesTheCastersOwnLead() {
        assertTrue(LeadRelease.releasesOwnLead(AbilityJson.decode("yarn_lead").behaviors(), CASTER,
                new Led(CASTER, LEAD_DAMAGE)));
    }

    @Test
    void leadAtAMobAnotherPlayerLedReleasesNothing() {
        assertFalse(LeadRelease.releasesOwnLead(AbilityJson.decode("yarn_lead").behaviors(), CASTER,
                new Led(OTHER, LEAD_DAMAGE)));
    }

    @Test
    void anAbilityThatDoesNotLeadReleasesNothing() {
        assertFalse(LeadRelease.releasesOwnLead(AbilityJson.decode("deep_shriek").behaviors(), CASTER,
                new Led(CASTER, LEAD_DAMAGE)));
    }
}
