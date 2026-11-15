package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import com.mercuriusxeno.goo.client.overlay.RippleRings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Meteo's blast zone, drawn at the aimed target while its charge holds, is
 * the held dome's rings alone: no sphere shell, the rings rippling over the
 * aimed surface out to the meteor's blast reach on the clock
 * (decisions meteo-blast-zone-is-ground-rings-only,
 * active-target-indicators-carry-depth-and-animation).
 */
class MeteoBlastZoneTest {

    /** Meteo's meteor explodes at power 4, as its JSON names. */
    private static final float METEOR_POWER = 4.0f;

    @Test
    void zoneHoldsNoSphereShell() {
        assertTrue(MeteoBlastZone.INSTANCE.heldLayers().isEmpty());
    }

    @Test
    void zoneRingsReachTheMeteorsBlastReach() {
        AbilityDefinition meteo = AbilityJson.decode("astral_meteo");
        HeldGhost ghost = MeteoBlastZone.INSTANCE.ghost(meteo.area(), meteo.behaviors());
        assertEquals((float) ExplosionMarch.maxReach(METEOR_POWER), ghost.ringRadius(), 1e-6f);
    }

    @Test
    void zoneRingPhaseAdvancesWithTime() {
        assertFalse(RippleRings.ringPhases(0.0)[0] == RippleRings.ringPhases(0.4)[0]);
    }
}
