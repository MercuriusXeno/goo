package com.mercuriusxeno.goo.block.crystallizer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crystallizer draws the operator's model as ruled (decision crystallizer-emits-chrysm):
 * every facing, knob and active state draws one body, active naming the active texture,
 * and one dial model; the dials turn about z on (8, 7, 15.5) at 22.5, 0 and -22.5 and
 * light 3, 7 and 10 pixels of the front red line. Reads the resources through the classpath.
 */
class CrystallizerResourcesTest {

    private static final String BLOCKSTATE = "/assets/goo/blockstates/crystallizer.json";
    private static final String GOO = "goo:";
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");
    private static final List<String> KNOB_POSITIONS = List.of("1", "2", "3");
    private static final List<Double> KNOB_ANGLES = List.of(22.5, 0.0, -22.5);
    private static final List<Double> LIT_PIXELS = List.of(3.0, 7.0, 10.0);
    private static final double LINE_PIXELS = 10;
    private static final List<Double> PIVOT = List.of(8.0, 7.0, 15.5);

    @Test
    void everyStateDrawsOneBodyAndOneDial() throws Exception {
        List<JsonObject> parts = new ArrayList<>();
        for (JsonElement part : readJson(BLOCKSTATE).getAsJsonArray("multipart")) {
            parts.add(part.getAsJsonObject());
        }
        for (String facing : FACINGS) {
            for (String knob : KNOB_POSITIONS) {
                for (String active : List.of("false", "true")) {
                    List<String> drawn = new ArrayList<>();
                    for (JsonObject part : parts) {
                        JsonObject when = part.getAsJsonObject("when");
                        if (matches(when, "facing", facing) && matches(when, "knob", knob)
                                && matches(when, "active", active)) {
                            drawn.add(part.getAsJsonObject("apply").get("model").getAsString());
                        }
                    }
                    String body = "true".equals(active) ? "goo:block/crystallizer_body_active" : "goo:block/crystallizer_body";
                    assertEquals(List.of(body, "goo:block/crystallizer_dial_" + knob), drawn,
                            facing + ", knob " + knob + ", active " + active);
                }
            }
        }
    }

    @Test
    void theActiveBodySwapsOnlyItsTexture() throws Exception {
        JsonObject active = readJson(modelPath("goo:block/crystallizer_body_active"));
        assertEquals("goo:block/crystallizer_body", active.get("parent").getAsString());
        assertEquals("goo:block/crystallizer_active", active.getAsJsonObject("textures").get("body").getAsString());
        assertTrue(!active.has("elements"), "the active body keeps the idle geometry");
    }

    @Test
    void dialsTurnOnTheirPivotAndLightTheirPixels() throws Exception {
        for (int i = 0; i < KNOB_POSITIONS.size(); i++) {
            String path = modelPath("goo:block/crystallizer_dial_" + KNOB_POSITIONS.get(i));
            for (JsonElement e : readJson(path).getAsJsonArray("elements")) {
                JsonObject element = e.getAsJsonObject();
                String name = element.get("name").getAsString();
                if ("strength_line".equals(name) || "strength_line_unlit".equals(name)) {
                    continue;
                }
                JsonObject rotation = element.getAsJsonObject("rotation");
                assertEquals("z", rotation.get("axis").getAsString(), path);
                assertEquals(KNOB_ANGLES.get(i), rotation.get("angle").getAsDouble(), path);
                List<Double> origin = new ArrayList<>();
                rotation.getAsJsonArray("origin").forEach(v -> origin.add(v.getAsDouble()));
                assertEquals(PIVOT, origin, path);
            }
        }
    }

    @Test
    void theStrengthLineLightsOnlyTheKnobsPixelsActiveOrIdle() throws Exception {
        for (int i = 0; i < KNOB_POSITIONS.size(); i++) {
            String path = modelPath("goo:block/crystallizer_dial_" + KNOB_POSITIONS.get(i));
            JsonObject model = readJson(path);
            double lit = 0;
            double unlit = 0;
            for (JsonElement e : model.getAsJsonArray("elements")) {
                JsonObject element = e.getAsJsonObject();
                String name = element.get("name").getAsString();
                double width = element.getAsJsonArray("to").get(0).getAsDouble()
                        - element.getAsJsonArray("from").get(0).getAsDouble();
                String texture = element.getAsJsonObject("faces").has("south")
                        ? element.getAsJsonObject("faces").getAsJsonObject("south").get("texture").getAsString() : "";
                if ("strength_line".equals(name)) {
                    lit = width;
                    assertEquals("#lit", texture, path);
                } else if ("strength_line_unlit".equals(name)) {
                    unlit = width;
                    assertEquals("#dial", texture, path);
                }
            }
            assertEquals(LIT_PIXELS.get(i), lit, path);
            assertEquals(LINE_PIXELS - LIT_PIXELS.get(i), unlit, path);
        }
        assertEquals("goo:block/crystallizer", readJson(modelPath("goo:block/crystallizer_dial_1"))
                .getAsJsonObject("textures").get("dial").getAsString(), "the unlit strip reads the idle texture");
    }

    @Test
    void everyNamedTextureShips() throws Exception {
        List<String> models = List.of("goo:block/crystallizer", "goo:block/crystallizer_body",
                "goo:block/crystallizer_body_active", "goo:block/crystallizer_dial_1",
                "goo:block/crystallizer_dial_2", "goo:block/crystallizer_dial_3");
        for (String model : models) {
            JsonObject json = readJson(modelPath(model));
            if (!json.has("textures")) {
                continue;
            }
            for (var texture : json.getAsJsonObject("textures").entrySet()) {
                String name = texture.getValue().getAsString();
                assertTrue(name.startsWith(GOO), name + " should be a goo texture");
                String path = "/assets/goo/textures/" + name.substring(GOO.length()) + ".png";
                try (InputStream in = CrystallizerResourcesTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, "Texture missing on classpath: " + path);
                }
            }
        }
    }

    private static boolean matches(JsonObject when, String property, String value) {
        return when == null || !when.has(property) || when.get(property).getAsString().equals(value);
    }

    private static String modelPath(String model) {
        return "/assets/goo/models/" + model.substring(GOO.length()) + ".json";
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = CrystallizerResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
