package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.lunar.Lunar;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The one astral_lunar ability shows the name Terran and the Terran icon to a player standing on the moon, and the
 * name Lunar and the Lunar icon everywhere else.
 * decision lunar-reads-as-terran-while-you-are-on-the-moon
 */
class ShownFaceTest {

    private static final String LUNAR_KEY = "goo.ability.astral.lunar";
    private static final String TERRAN_KEY = "goo.ability.astral.terran";
    private static final Identifier LUNAR_ICON = Identifier.parse("goo:textures/goo/ability/astral_lunar.png");
    private static final Identifier TERRAN_ICON = Identifier.parse("goo:textures/goo/ability/astral_terran.png");
    private static final String EN_US = "/assets/goo/lang/en_us.json";

    private final AbilityDefinition lunar = AbilityJson.decode("astral_lunar");

    @Test
    void onTheMoonLunarShowsTerran() {
        assertEquals(new ShownFace(TERRAN_KEY, TERRAN_ICON), lunar.shownIn(Lunar.LEVEL));
    }

    @Test
    void inTheOverworldLunarShowsLunar() {
        assertEquals(new ShownFace(LUNAR_KEY, LUNAR_ICON), lunar.shownIn(Level.OVERWORLD));
    }

    @Test
    void terranReadsTerran() throws IOException {
        try (InputStream in = ShownFaceTest.class.getResourceAsStream(EN_US)) {
            assertNotNull(in, EN_US);
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("Terran", lang.has(TERRAN_KEY) ? lang.get(TERRAN_KEY).getAsString() : null);
        }
    }
}
