package com.mercuriusxeno.goo.ability.stasis;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Only a strike from an attacker frees a mob in stasis; harm with no
 * attacker leaves it frozen.
 * stasis-holds-mob-with-golden-shimmer
 */
class StasisEventsTest {

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
}
