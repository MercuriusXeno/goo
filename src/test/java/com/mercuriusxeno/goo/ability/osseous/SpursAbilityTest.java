package com.mercuriusxeno.goo.ability.osseous;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.SpursStep;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spurs, read from the shipped osseous_spurs.json through the ability codec, is Osseous's residual held stream: it
 * drains on placement, lays its cell through a spurs step, and lang names it Spurs.
 * decision spurs-shoots-bone-spikes-at-walkers
 */
class SpursAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";

    @Test
    void spursIsOsseousResidualStreamDrainingOnPlacement() throws IOException {
        AbilityDefinition spurs = AbilityJson.decode("osseous_spurs");

        assertEquals(GooTypes.OSSEOUS, spurs.gooType());
        assertEquals(AbilityBadge.RESIDUAL, spurs.badge());
        assertTrue(spurs.delivery().drainsOnPlacement(), "spurs pays by the ticks it is held");
        assertTrue(SpursStep.in(spurs.behaviors()).isPresent(), "spurs lays no spurs step");
        assertEquals("Spurs", lang().get(spurs.displayName()).getAsString());
    }

    private static JsonObject lang() throws IOException {
        try (InputStream in = SpursAbilityTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
