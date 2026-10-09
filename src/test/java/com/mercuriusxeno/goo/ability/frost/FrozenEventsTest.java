package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which hits a frozen gauge makes land harder, the physical ones armor
 * checks, and the flat bonus such a hit gains after armor.
 */
class FrozenEventsTest {

    private static final FrostCurve NOVA = new FrostCurve(300, 0.005f, 0.5f, 3f);
    private static final float AFTER_ARMOR = 4f;

    private static DamageSource source(boolean bypassesArmor) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(DamageTypeTags.BYPASSES_ARMOR)).thenReturn(bypassesArmor);
        return source;
    }

    @Test
    void aHitArmorChecksIsPhysical() {
        assertTrue(FrozenEvents.isPhysical(source(false)));
    }

    @Test
    void aHitBypassingArmorIsNotPhysical() {
        assertFalse(FrozenEvents.isPhysical(source(true)));
    }

    // nova-ring-grows-with-the-hold: front-loaded, whole from the first frost
    @Test
    void aPhysicalHitOnAnyFrostGainsTheWholeBonusAfterArmor() {
        Frozen touched = Frozen.NONE.add(0.1f, NOVA, 0L);
        assertEquals(AFTER_ARMOR + 3f, FrozenEvents.withPhysicalBonus(touched, source(false), AFTER_ARMOR));
    }

    @Test
    void aThawedMobGainsNoBonus() {
        assertEquals(AFTER_ARMOR, FrozenEvents.withPhysicalBonus(Frozen.NONE, source(false), AFTER_ARMOR));
    }

    @Test
    void aHitBypassingArmorGainsNoBonus() {
        Frozen touched = Frozen.NONE.add(0.1f, NOVA, 0L);
        assertEquals(AFTER_ARMOR, FrozenEvents.withPhysicalBonus(touched, source(true), AFTER_ARMOR));
    }
}
