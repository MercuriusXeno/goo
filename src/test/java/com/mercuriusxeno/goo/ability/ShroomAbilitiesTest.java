package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Shroom's shipped roster and its residual goo, read from the shipped
 * ability files and en_us.json through the classpath: Spore left, and the
 * residual goo ships as Spores.
 * colonize-is-dropped-and-its-power-moves-into-mycosis-goo
 * spores-is-shrooms-residual-a-passive-mycosis
 */
class ShroomAbilitiesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String SPORES = "shroom_spores";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = ShroomAbilitiesTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void shroomShipsSporesTransmitAndSight() {
        Set<String> shroom = AbilityJson.files().stream().map(AbilityJson::decode)
                .filter(ability -> ability.gooType() == GooTypes.SHROOM)
                .map(ability -> ability.id().getPath())
                .collect(Collectors.toSet());

        assertEquals(Set.of(SPORES, "shroom_transmit", "shroom_sight"), shroom);
    }

    @Test
    void sporesWearsTheResidualBadge() {
        assertEquals(AbilityBadge.RESIDUAL, AbilityJson.decode(SPORES).badge());
    }

    @Test
    void sporesIsShownAsSpores() {
        String key = AbilityJson.decode(SPORES).displayName();

        assertEquals("Spores", lang.get(key).getAsString(), key);
    }
}
