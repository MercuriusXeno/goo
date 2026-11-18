package com.mercuriusxeno.goo.ability.tartarus;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.TartarusStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nether's Tartarus loads from nether_tartarus.json as a channeled gate requiring the netherrack recipe, and en_us
 * names it with a description, both read from the classpath.
 */
class TartarusAbilityTest {

    private static final String LANG = "assets/goo/lang/en_us.json";
    private static final String NAME_KEY = "goo.ability.nether.tartarus";

    private final AbilityDefinition tartarus = AbilityJson.decode("nether_tartarus");

    /** decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone */
    @Test
    void isANetherAbilityRequiringTheNetherrackRecipe() {
        assertEquals(GooTypes.NETHER, tartarus.gooType());
        assertEquals(List.of(Identifier.withDefaultNamespace("netherrack")), tartarus.requires());
    }

    /** decision tartarus-summons-a-gate-to-the-nether-like-end-and-zone */
    @Test
    void channelsTheTartarusGate() {
        assertEquals(1, tartarus.behaviors().size());
        assertInstanceOf(TartarusStep.class, tartarus.behaviors().getFirst());
    }

    @Test
    void enUsNamesItTartarusWithADescription() throws IOException {
        assertEquals(NAME_KEY, tartarus.displayName());
        try (InputStream in = TartarusAbilityTest.class.getClassLoader().getResourceAsStream(LANG)) {
            assertNotNull(in, LANG + " should ship");
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("Tartarus", lang.get(NAME_KEY).getAsString());
            assertTrue(lang.has(tartarus.description()), tartarus.description());
        }
    }
}
