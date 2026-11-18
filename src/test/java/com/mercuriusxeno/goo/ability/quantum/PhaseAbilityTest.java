package com.mercuriusxeno.goo.ability.quantum;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Quantum's world goo ships as quantum_phase.json, read as Phase with a
 * description.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 */
class PhaseAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String PHASE_KEY = "goo.ability.quantum.phase";

    @Nested
    class Name {

        @Test
        void phaseReadsPhaseWithADescription() throws IOException {
            JsonObject lang = classpathJson(EN_US);

            assertNotNull(lang.get(PHASE_KEY), PHASE_KEY);
            assertEquals("Phase", lang.get(PHASE_KEY).getAsString());
            assertNotNull(lang.get(PHASE_KEY + ".description"), PHASE_KEY + ".description");
            assertFalse(lang.get(PHASE_KEY + ".description").getAsString().isBlank(), PHASE_KEY + ".description");
        }

        @Test
        void phasesAbilityNamesThePhaseDisplayKey() {
            assertEquals(PHASE_KEY, AbilityJson.decode("quantum_phase").displayName());
        }
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = PhaseAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
