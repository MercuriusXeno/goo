package com.mercuriusxeno.goo.ability.held;

import com.mercuriusxeno.goo.ability.program.Lux;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lux's standing and its night vision: held Lux stands until its effect
 * ends, a brew's for its duration, and only Lux's endless hidden night
 * vision reads as Lux's, so a potion's is neither hidden nor removed.
 * decision lux-night-vision-without-particles
 */
class LuxEventsTest {

    private static final long NOW = 1_000L;
    private static final int HOUR = 72_000;

    @Test
    void heldLuxStandsUntilItsEffectEnds() {
        assertTrue(Lux.NONE.hold().standsAt(Long.MAX_VALUE - 1));
        assertFalse(Lux.NONE.standsAt(NOW));
    }

    @Test
    void aBrewStandsForItsDurationAndNeverShortensWhatStands() {
        Lux brewed = Lux.NONE.brew(HOUR, NOW);
        assertEquals(NOW + HOUR, brewed.expiresAt());
        assertEquals(Lux.NEVER_EXPIRES, Lux.NONE.hold().brew(HOUR, NOW).expiresAt());
    }

    @Test
    void onlyLuxsHiddenEndlessInstanceReadsAsLuxs() {
        assertTrue(LuxEvents.isLuxVision(true, true, false, false));
        assertFalse(LuxEvents.isLuxVision(false, false, true, true), "a potion's night vision");
        assertFalse(LuxEvents.isLuxVision(true, true, false, true), "an endless instance showing its icon");
        assertFalse(LuxEvents.isLuxVision(true, false, false, false), "an endless instance not ambient");
        assertFalse(LuxEvents.isLuxVision((net.minecraft.world.effect.MobEffectInstance) null));
    }
}
