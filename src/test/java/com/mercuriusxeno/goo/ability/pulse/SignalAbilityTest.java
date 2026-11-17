package com.mercuriusxeno.goo.ability.pulse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Pulse's world goo ships as pulse_signal.json under the display key
 * goo.ability.pulse.signal, which en_us.json reads as Signal with a description.
 * extend-is-dropped-for-signal-an-in-world-pulse-goo
 */
class SignalAbilityTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String SIGNAL_JSON = "/data/goo/goo_abilities/pulse_signal.json";
    private static final String SIGNAL_KEY = "goo.ability.pulse.signal";
    private static final String DESCRIPTION_KEY = SIGNAL_KEY + ".description";

    @Test
    void signalReadsSignalWithADescription() throws IOException {
        JsonObject lang = classpathJson(EN_US);

        assertNotNull(lang.get(SIGNAL_KEY), SIGNAL_KEY);
        assertEquals("Signal", lang.get(SIGNAL_KEY).getAsString());
        assertNotNull(lang.get(DESCRIPTION_KEY), DESCRIPTION_KEY);
        assertFalse(lang.get(DESCRIPTION_KEY).getAsString().isBlank(), DESCRIPTION_KEY);
    }

    @Test
    void signalsAbilityNamesTheSignalDisplayKey() throws IOException {
        assertEquals(SIGNAL_KEY, classpathJson(SIGNAL_JSON).get("displayName").getAsString());
    }

    private static JsonObject classpathJson(String path) throws IOException {
        try (InputStream in = SignalAbilityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
