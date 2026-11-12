package com.mercuriusxeno.goo.ability.quantum;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quantum's wormhole ability ships as goo:quantum_tunnel, shown as Tunnel with a wormhole
 * description and its former icon, and the goo:quantum_hole file and lang keys are gone.
 * decision quantum-hole-is-renamed-tunnel
 */
class TunnelNameTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String TUNNEL_KEY = "goo.ability.quantum.tunnel";
    private static final String HOLE_KEY = "goo.ability.quantum.hole";
    private static final String TUNNEL_ICON = "/assets/goo/textures/goo/ability/quantum_tunnel.png";
    private static final List<String> DESCRIPTION_WORDS = List.of("wormhole", "wall", "opening");

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = TunnelNameTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void tunnelFileCarriesTheTunnelDisplayName() {
        JsonObject tunnel = JsonParser.parseString(AbilityJson.read("quantum_tunnel")).getAsJsonObject();
        assertEquals(TUNNEL_KEY, tunnel.get("displayName").getAsString());
    }

    @Test
    void holeFileNoLongerShips() {
        String resource = AbilityJson.ABILITIES_DIR + "/quantum_hole.json";
        assertNull(TunnelNameTest.class.getClassLoader().getResource(resource), resource);
    }

    @Test
    void tunnelKeyReadsTunnel() {
        assertEquals("Tunnel", lang.get(TUNNEL_KEY).getAsString());
    }

    @Test
    void tunnelDescriptionTellsTheWormholeThroughTheWall() {
        String description = lang.get(TUNNEL_KEY + ".description").getAsString().toLowerCase(Locale.ROOT);
        for (String word : DESCRIPTION_WORDS) {
            assertTrue(description.contains(word), () -> TUNNEL_KEY + ".description lacks " + word + ": " + description);
        }
    }

    @Test
    void langHoldsNoQuantumHoleKey() {
        for (String key : lang.keySet()) {
            assertFalse(key.startsWith(HOLE_KEY), () -> "en_us still holds " + key);
        }
    }

    @Test
    void tunnelIconStandsAtTheConventionPath() {
        assertNotNull(TunnelNameTest.class.getResource(TUNNEL_ICON), TUNNEL_ICON);
    }
}
