package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Sculk's shipped roster and its residual goo, read from the shipped ability files and en_us.json through the
 * classpath: Mycosis is the held stream, and Host and Spores ship as one residual, Creep.
 * host-and-spores-combine-into-one-residual
 * mycosis-is-put-back-as-the-held-spore-stream
 */
class SculkAbilitiesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String CREEP = "sculk_creep";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = SculkAbilitiesTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static List<AbilityDefinition> shipped() {
        return AbilityJson.files().stream().map(AbilityJson::decode).toList();
    }

    @Test
    void sculkShipsMycosisCreepTransmitAndSight() {
        Set<String> sculk = shipped().stream().filter(ability -> ability.gooType() == GooTypes.SCULK)
                .map(ability -> ability.id().getPath()).collect(Collectors.toSet());

        assertEquals(Set.of("sculk_mycosis", CREEP, "sculk_transmit", "sculk_sight"), sculk);
    }

    @Test
    void sculksOneResidualIsCreep() {
        Set<Identifier> residuals = shipped().stream().filter(ability -> ability.gooType() == GooTypes.SCULK)
                .filter(ability -> ability.badge() == AbilityBadge.RESIDUAL).map(AbilityDefinition::id)
                .collect(Collectors.toSet());

        assertEquals(Set.of(Identifier.fromNamespaceAndPath("goo", CREEP)), residuals);
    }

    @Test
    void noAbilityIsKeyedHostOrSpores() {
        List<Identifier> old = shipped().stream().map(AbilityDefinition::id)
                .filter(id -> id.getPath().endsWith("host") || id.getPath().endsWith("spores")).toList();

        assertEquals(List.of(), old);
    }

    @Test
    void creepIsShownAsCreepWithADescription() {
        AbilityDefinition creep = AbilityJson.decode(CREEP);

        assertEquals("Creep", lang.get(creep.displayName()).getAsString(), creep.displayName());
        assertFalse(lang.get(creep.description()).getAsString().isBlank(), creep.description());
    }
}
