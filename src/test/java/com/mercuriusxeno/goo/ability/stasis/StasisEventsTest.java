package com.mercuriusxeno.goo.ability.stasis;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Only a strike from an attacker frees a mob in stasis, and not the punch
 * that lands it; harm with no attacker leaves it frozen.
 * stasis-holds-mob-with-golden-shimmer
 */
class StasisEventsTest {

    private static final long LANDED = 1000L;

    @Test
    void aStrikeCausedByAnEntityFrees() {
        DamageSource source = mock(DamageSource.class);
        when(source.getEntity()).thenReturn(mock(Entity.class));

        assertTrue(StasisEvents.struckByAttacker(source));
    }

    @Test
    void aProjectileWithNoOwnerStillFrees() {
        DamageSource source = mock(DamageSource.class);
        when(source.getDirectEntity()).thenReturn(mock(Entity.class));

        assertTrue(StasisEvents.struckByAttacker(source));
    }

    @Test
    void harmWithNoAttackerLeavesTheMobFrozen() {
        assertFalse(StasisEvents.struckByAttacker(mock(DamageSource.class)));
    }

    @Test
    void theLandingPunchFreesNothing() {
        assertFalse(StasisEvents.pastLandingGrace(LANDED, LANDED));
        assertFalse(StasisEvents.pastLandingGrace(LANDED, LANDED + StasisEvents.LANDING_GRACE_TICKS));
    }

    @Test
    void aStrikePastTheGraceFrees() {
        assertTrue(StasisEvents.pastLandingGrace(LANDED, LANDED + StasisEvents.LANDING_GRACE_TICKS + 1));
        assertTrue(StasisEvents.pastLandingGrace(null, LANDED));
    }
}
