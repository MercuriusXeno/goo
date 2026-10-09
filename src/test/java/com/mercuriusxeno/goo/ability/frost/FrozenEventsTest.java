package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which hits a frozen gauge makes land harder: the physical ones, those armor checks.
 */
class FrozenEventsTest {

    @Test
    void aHitArmorChecksIsPhysical() {
        DamageSource punch = mock(DamageSource.class);
        when(punch.is(DamageTypeTags.BYPASSES_ARMOR)).thenReturn(false);
        assertTrue(FrozenEvents.isPhysical(punch));
    }

    @Test
    void aHitBypassingArmorIsNotPhysical() {
        DamageSource freeze = mock(DamageSource.class);
        when(freeze.is(DamageTypeTags.BYPASSES_ARMOR)).thenReturn(true);
        assertFalse(FrozenEvents.isPhysical(freeze));
    }
}
