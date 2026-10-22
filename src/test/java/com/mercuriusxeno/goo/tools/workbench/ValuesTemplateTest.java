package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the template a loadout starts from: every item named, a group and an empty #group entry per tag of
 * two or more members, every item no such tag covers as an empty entry ({}: the loader throws on an empty
 * expression string), the goo types for reference, and the
 * text read by the game's loader (decision template-first-then-defaults-by-convention).
 */
class ValuesTemplateTest {

    private static final ValuesTemplate.Loadout LOADOUT = new ValuesTemplate.Loadout(
            List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:apple", "goo:widget", "minecraft:stick",
                    "minecraft:bread"),
            Map.of("minecraft:logs", List.of("minecraft:oak_log", "minecraft:birch_log"),
                    "c:fruits", List.of("minecraft:apple")),
            List.of("metal", "leaf", "jelly"));

    /**
     * The two-member tag alone becomes a group with an empty #group entry, the four items it leaves stand empty,
     * and the loader reads the text.
     */
    @Test
    void templateNamesEveryItemGroupedByTag() throws IOException {
        String text = ValuesTemplate.build(LOADOUT).serialize();
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        JsonObject groups = root.getAsJsonObject("_groups");
        assertEquals(List.of("logs"), List.copyOf(groups.keySet()));
        assertEquals(List.of("oak_log", "birch_log"),
                groups.getAsJsonArray("logs").asList().stream().map(JsonElement::getAsString).toList());
        assertEquals(new JsonObject(), root.get("#logs"));
        for (String uncovered : List.of("apple", "goo:widget", "stick", "bread")) {
            assertEquals(new JsonObject(), root.get(uncovered), uncovered);
        }
        assertEquals(List.of("metal", "leaf", "jelly"),
                root.getAsJsonArray("_goo_types").asList().stream().map(JsonElement::getAsString).toList());
        assertDoesNotThrow(() -> GooBaseValuesPreview.baseValues(text));
    }

    /**
     * For a file that already stands, the template lists the tags no group carries and the items nothing covers.
     */
    @Test
    void leftoversListWhatTheFileLeavesOut() {
        BaseValuesModel model = BaseValuesModel.parse("""
                {
                    "_constants": {},
                    "_groups": {},
                    "stick": {"leaf": 1}
                }
                """);
        ValuesTemplate.Leftovers leftovers = ValuesTemplate.leftovers(model, LOADOUT);
        assertEquals(List.of("minecraft:logs"), leftovers.tags());
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:apple", "goo:widget",
                "minecraft:bread"), leftovers.items());
    }
}
