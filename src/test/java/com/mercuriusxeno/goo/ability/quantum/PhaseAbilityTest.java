package com.mercuriusxeno.goo.ability.quantum;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.PhaseGooStep;
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
 * description, and the phase it lays lingers after stepping off for the
 * layers of the cell left times the seconds a layer the ability carries.
 * phase-goo-puts-what-stands-in-it-out-of-phase
 */
class PhaseAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String PHASE_KEY = "goo.ability.quantum.phase";
    private static final int TICKS_A_SECOND = 20;
    private static final int FULL_CELL = 15;

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

    @Nested
    class Linger {

        @Test
        void oneLayerLingersTheSecondsALayerTheAbilityCarries() {
            PhaseGooStep shipped = shipped();

            assertEquals(Math.round(shipped.lingerSecondsPerLayer() * TICKS_A_SECOND), shipped.lingerTicks(1));
        }

        @Test
        void fifteenLayersLingerFifteenTimesAsLongAsOne() {
            PhaseGooStep shipped = shipped();

            assertEquals(FULL_CELL * shipped.lingerTicks(1), shipped.lingerTicks(FULL_CELL));
        }
    }

    private static PhaseGooStep shipped() {
        return PhaseGooStep.in(AbilityJson.decode("quantum_phase").behaviors()).orElseThrow();
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = PhaseAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
