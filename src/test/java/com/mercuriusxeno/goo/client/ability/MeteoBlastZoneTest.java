package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ExplosionMarch;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Meteo's blast zone, drawn at the aimed target while its charge holds,
 * spans the meteor's blast reach and pulses on the clock, so it reads by
 * depth and animation rather than by color alone
 * (decisions spontaneous-abilities-cast-at-the-target-with-no-thrown-blob,
 * active-target-indicators-carry-depth-and-animation).
 */
class MeteoBlastZoneTest {

    /** Meteo's meteor explodes at power 4, as its JSON names. */
    private static final float METEOR_POWER = 4.0f;

    @Test
    void zoneSpansTheMeteorsBlastReach() {
        AbilityDefinition meteo = AbilityJson.decode("astral_meteo");
        HeldGhost ghost = MeteoBlastZone.INSTANCE.ghost(meteo.area(), meteo.behaviors());
        assertEquals((float) ExplosionMarch.maxReach(METEOR_POWER), ghost.domeRadius(), 1e-6f);
    }

    @Test
    void zonePulsesWithTime() {
        assertNotEquals(MeteoBlastZone.pulseAt(0.0), MeteoBlastZone.pulseAt(0.4));
    }
}
