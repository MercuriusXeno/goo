package com.mercuriusxeno.goo.block.alientech;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relay's models, read from its blockstate: each one's torch-head tip sits in the center 8x8 of the cell and
 * holds its center, and the blockstate names the lit model while the relay is lit and the unlit one while it is not.
 * decision relay-shrinks-so-its-tip-sits-centered-and-beams-join-tips
 * decision relay-tip-resembles-a-redstone-torch
 */
class RelayModelTest {

    private static final String BLOCKSTATE = "/assets/goo/blockstates/relay.json";
    private static final String MODEL_DIR = "/assets/goo/models/block/";
    private static final String TORCH_TEXTURE = "#torch";
    private static final double CENTER_LOW = 4;
    private static final double CENTER_HIGH = 12;
    private static final double CELL_CENTER = 8;
    private static final String LIT_MODEL = "goo:block/relay_lit";
    private static final String UNLIT_MODEL = "goo:block/relay";

    @Nested
    class Tip {

        @Test
        void everyModelsTorchTipSitsInTheCenterEightAndHoldsTheCellsCenter() {
            for (String model : modelsNamed()) {
                List<JsonObject> tip = torchElements(model);
                assertFalse(tip.isEmpty(), model + " should hold a tip wearing " + TORCH_TEXTURE);
                for (JsonObject element : tip) {
                    assertTrue(withinTheCenterEight(element), model + "'s tip element " + element.get("from") + " to "
                            + element.get("to") + " should span pixels within 4 to 12 on every axis");
                }
                assertTrue(tip.stream().anyMatch(RelayModelTest::holdsTheCellsCenter),
                        model + "'s tip should hold the cell's center (8,8,8)");
            }
        }
    }

    @Nested
    class LitState {

        @Test
        void litVariantsNameTheLitModelAndUnlitOnesTheUnlitModel() {
            Map<String, JsonElement> variants = read(BLOCKSTATE).getAsJsonObject("variants").asMap();
            assertFalse(variants.isEmpty(), "relay.json should hold variants");
            for (Map.Entry<String, JsonElement> variant : variants.entrySet()) {
                String model = variant.getValue().getAsJsonObject().get("model").getAsString();
                String expected = variant.getKey().contains("lit=true") ? LIT_MODEL : UNLIT_MODEL;
                assertTrue(variant.getKey().contains("lit="), variant.getKey() + " should name the lit state");
                assertEquals(expected, model, variant.getKey() + " names the wrong model");
            }
        }
    }

    private static List<String> modelsNamed() {
        List<String> models = new ArrayList<>();
        for (JsonElement variant : read(BLOCKSTATE).getAsJsonObject("variants").asMap().values()) {
            String model = variant.getAsJsonObject().get("model").getAsString();
            if (!models.contains(model)) {
                models.add(model);
            }
        }
        return models;
    }

    private static List<JsonObject> torchElements(String model) {
        JsonObject json = read(MODEL_DIR + model.substring(model.indexOf('/') + 1) + ".json");
        List<JsonObject> wearing = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("elements")) {
            JsonObject faces = element.getAsJsonObject().getAsJsonObject("faces");
            boolean torch = faces.asMap().values().stream()
                    .anyMatch(face -> TORCH_TEXTURE.equals(face.getAsJsonObject().get("texture").getAsString()));
            if (torch) {
                wearing.add(element.getAsJsonObject());
            }
        }
        return wearing;
    }

    private static boolean withinTheCenterEight(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        for (int axis = 0; axis < 3; axis++) {
            if (from.get(axis).getAsDouble() < CENTER_LOW || to.get(axis).getAsDouble() > CENTER_HIGH) {
                return false;
            }
        }
        return true;
    }

    private static boolean holdsTheCellsCenter(JsonObject element) {
        JsonArray from = element.getAsJsonArray("from");
        JsonArray to = element.getAsJsonArray("to");
        for (int axis = 0; axis < 3; axis++) {
            if (from.get(axis).getAsDouble() >= CELL_CENTER || to.get(axis).getAsDouble() <= CELL_CENTER) {
                return false;
            }
        }
        return true;
    }

    private static JsonObject read(String resource) {
        try (InputStream in = RelayModelTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource + " should be on the classpath");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
