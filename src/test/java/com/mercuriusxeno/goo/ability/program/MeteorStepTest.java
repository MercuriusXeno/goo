package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Meteo's meteor step, read from the shipped astral_meteo.json: the meteor falls for two seconds.
 */
class MeteorStepTest {

    private static final int TWO_SECONDS = 40;

    @Test
    void meteosFallTakesFortyTicks() {
        MeteorStep meteor = AbilityJson.decode("astral_meteo").behaviors().stream()
                .filter(MeteorStep.class::isInstance).map(MeteorStep.class::cast).findFirst().orElseThrow();
        assertEquals(TWO_SECONDS, meteor.fallTicks());
    }
}
