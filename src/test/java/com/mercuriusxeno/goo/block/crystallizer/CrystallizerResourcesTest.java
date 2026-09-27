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

/**
 * The crystallizer's knob draws as the operator ruled (decision crystallizer-emits-chrysm):
 * every facing and knob position draws the body and exactly one knob model, and
 * knob positions 1, 2 and 3 turn about the face's axis at -45, 0 and 45 degrees.
 * Reads the resources through the classpath.
 */
class CrystallizerResourcesTest {

    private static final String BLOCKSTATE = "/assets/goo/blockstates/crystallizer.json";
    private static final String KNOB_MODEL = "goo:block/crystallizer_knob_";
    private static final List<String> FACINGS = List.of("north", "east", "south", "west");
    private static final List<String> KNOB_POSITIONS = List.of("1", "2", "3");
    private static final List<Double> KNOB_ANGLES = List.of(-45.0, 0.0, 45.0);

    @Test
    void everyStateDrawsTheBodyAndItsOneKnob() throws Exception {
        List<JsonObject> parts = new ArrayList<>();
        for (JsonElement part : readJson(BLOCKSTATE).getAsJsonArray("multipart")) {
            parts.add(part.getAsJsonObject());
        }
        for (String facing : FACINGS) {
            for (String knob : KNOB_POSITIONS) {
                List<String> drawn = new ArrayList<>();
                for (JsonObject part : parts) {
                    JsonObject when = part.getAsJsonObject("when");
                    if (matches(when, "facing", facing) && matches(when, "knob", knob)) {
                        drawn.add(part.getAsJsonObject("apply").get("model").getAsString());
                    }
                }
                assertEquals(List.of("goo:block/crystallizer_body", KNOB_MODEL + knob), drawn,
                        "facing " + facing + ", knob " + knob);
            }
        }
    }

    @Test
    void knobPositionsTurnAboutTheFaceAxisAtMinusFortyFiveZeroAndFortyFive() throws Exception {
        for (int i = 0; i < KNOB_POSITIONS.size(); i++) {
            String path = "/assets/goo/models/block/crystallizer_knob_" + KNOB_POSITIONS.get(i) + ".json";
            for (JsonElement element : readJson(path).getAsJsonArray("elements")) {
                JsonObject rotation = element.getAsJsonObject().getAsJsonObject("rotation");
                assertEquals("z", rotation.get("axis").getAsString(), path);
                assertEquals(KNOB_ANGLES.get(i), rotation.get("angle").getAsDouble(), path);
            }
        }
    }

    private static boolean matches(JsonObject when, String property, String value) {
        return !when.has(property) || when.get(property).getAsString().equals(value);
    }

    private static JsonObject readJson(String path) throws Exception {
        try (InputStream in = CrystallizerResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Resource missing on classpath: " + path);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
