package com.mercuriusxeno.goo.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The goo item's model, read from assets/goo/items/goo.json through the
 * classpath: dropped and framed it draws the goo cuboid's special model
 * (decision goo-item-in-world-draws-as-the-palm-cuboid), held in either hand,
 * first or third person, it draws through the glove's renderer on the glove's
 * own placement, the glove without its model part (decision
 * raw-goo-items-work-bare-handed-for-the-held-type), and the GUI keeps the
 * sprite tiers dispatched on goo:goo_size.
 */
class GooItemModelTest {

    private static final String GOO_ITEM_MODEL = "assets/goo/items/goo.json";
    private static final int SPRITE_TIERS = 4;

    @ParameterizedTest
    @ValueSource(strings = {"firstperson_lefthand", "firstperson_righthand", "thirdperson_lefthand",
            "thirdperson_righthand"})
    void aHeldContextDrawsThroughTheGlovesRendererOnTheGlovesPlacement(String context) throws IOException {
        JsonObject model = modelFor(context);

        assertEquals("minecraft:special", model.get("type").getAsString(), context + " should draw a special model");
        assertEquals("goo:glove_goo", model.getAsJsonObject("model").get("type").getAsString(),
                context + " should draw through the glove's renderer");
        assertEquals("goo:item/goo_glove", model.get("base").getAsString(),
                context + " should stand where the glove stands");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ground", "fixed"})
    void aLevelContextDrawsTheGooCuboid(String context) throws IOException {
        JsonObject model = modelFor(context);

        assertEquals("minecraft:special", model.get("type").getAsString(), context + " should draw a special model");
        assertEquals("goo:goo_cuboid", model.getAsJsonObject("model").get("type").getAsString(),
                context + " should draw the goo cuboid");
    }

    @Test
    void theGuiContextDrawsTheSpriteTiersBySize() throws IOException {
        JsonObject model = modelFor("gui");

        assertEquals("minecraft:range_dispatch", model.get("type").getAsString());
        assertEquals("goo:goo_size", model.get("property").getAsString());
        assertEquals(SPRITE_TIERS, model.getAsJsonArray("entries").size() + 1,
                "the fallback and each entry should be one sprite tier");
    }

    /** The model the item's display-context select answers for a context. */
    private static JsonObject modelFor(String context) throws IOException {
        JsonObject select = itemModel();
        assertEquals("minecraft:display_context", select.get("property").getAsString(),
                "the goo item's model should select on the display context");
        for (JsonElement entry : select.getAsJsonArray("cases")) {
            JsonObject when = entry.getAsJsonObject();
            if (matches(when.get("when"), context)) {
                return when.getAsJsonObject("model");
            }
        }
        return select.getAsJsonObject("fallback");
    }

    private static boolean matches(JsonElement when, String context) {
        if (when instanceof JsonArray values) {
            return values.asList().stream().anyMatch(value -> value.getAsString().equals(context));
        }
        return when.getAsString().equals(context);
    }

    private static JsonObject itemModel() throws IOException {
        try (InputStream stream = GooItemModelTest.class.getClassLoader().getResourceAsStream(GOO_ITEM_MODEL)) {
            assertNotNull(stream, GOO_ITEM_MODEL + " should be on the classpath");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("model");
            }
        }
    }
}
