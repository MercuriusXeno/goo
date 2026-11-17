package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.program.ShatterStep;
import com.mercuriusxeno.goo.ability.program.Variables;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each Shatter cut has the chance crystal_shatter.json ships of taking one
 * layer from the cell under the shards: a draw under the chance takes it, a
 * draw over it leaves the goo whole.
 * each-shatter-cut-has-a-chance-to-deplete-a-layer
 */
class ShatterCutTest {

    private static final String SHATTER_JSON = "/data/goo/goo_abilities/crystal_shatter.json";
    private static final int FIVE_LAYERS = 5;
    private static final double NUDGE = 1e-6;
    private static final ShatterStep SHATTER = ShatterStep.in(AbilityJson.decode("crystal_shatter").behaviors())
            .orElseThrow();

    @Test
    void aDrawUnderTheChanceTakesALayerAndADrawOverItLeavesTheGooWhole() {
        double chance = ShatterCut.depleteChanceOf(SHATTER, Variables.NONE);

        assertEquals(FIVE_LAYERS - 1, ShatterCut.layersAfterCut(FIVE_LAYERS, chance - NUDGE, chance));
        assertEquals(FIVE_LAYERS, ShatterCut.layersAfterCut(FIVE_LAYERS, chance + NUDGE, chance));
    }

    @Test
    void theChanceIsTheOneCrystalShatterJsonCarries() throws IOException {
        double shipped = shatterStepJson().get("deplete_chance").getAsDouble();

        assertTrue(shipped > 0 && shipped < 1, "the shipped chance " + shipped + " is no chance");
        assertEquals(shipped, ShatterCut.depleteChanceOf(SHATTER, Variables.NONE), NUDGE);
    }

    private static JsonObject shatterStepJson() throws IOException {
        try (InputStream in = ShatterCutTest.class.getResourceAsStream(SHATTER_JSON)) {
            assertTrue(in != null, SHATTER_JSON + " is not on the classpath");
            JsonObject ability = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return ability.getAsJsonArray("behaviors").get(0).getAsJsonObject();
        }
    }
}
