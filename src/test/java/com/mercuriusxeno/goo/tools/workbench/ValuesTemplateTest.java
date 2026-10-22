package com.mercuriusxeno.goo.tools.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Tests the template a loadout starts from: every item no entry or group covers as an empty entry ({}: the
 * loader throws on an empty expression string), no group made from a tag, since tags are supported for valuing
 * and not mandated, the goo types for reference, and the text read by the game's loader
 * (decision template-first-then-defaults-by-convention).
 */
class ValuesTemplateTest {

    private static final ValuesTemplate.Loadout LOADOUT = new ValuesTemplate.Loadout(
            List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:apple", "goo:widget", "minecraft:stick",
                    "minecraft:bread"),
            Map.of("minecraft:logs", List.of("minecraft:oak_log", "minecraft:birch_log"),
                    "c:fruits", List.of("minecraft:apple")),
            List.of("metal", "leaf", "jelly"));

    /**
     * No tag becomes a group, all six items stand empty, and the loader reads the text.
     */
    @Test
    void templateNamesEveryItemAndGroupsNoTag() {
        String text = ValuesTemplate.build(LOADOUT).serialize();
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(new JsonObject(), root.getAsJsonObject("_groups"));
        assertFalse(root.has("#logs"));
        for (String uncovered : List.of("oak_log", "birch_log", "apple", "goo:widget", "stick", "bread")) {
            assertEquals(new JsonObject(), root.get(uncovered), uncovered);
        }
        assertEquals(List.of("metal", "leaf", "jelly"),
                root.getAsJsonArray("_goo_types").asList().stream().map(JsonElement::getAsString).toList());
        assertDoesNotThrow(() -> GooBaseValuesPreview.baseValues(text));
    }

    /**
     * For a file that already stands, the template lists the items nothing covers and no tag.
     */
    @Test
    void leftoversListTheItemsTheFileLeavesOut() {
        BaseValuesModel model = BaseValuesModel.parse("""
                {
                    "_constants": {},
                    "_groups": {},
                    "stick": {"leaf": 1}
                }
                """);
        List<String> leftovers = ValuesTemplate.leftovers(model, LOADOUT);
        assertEquals(List.of("minecraft:oak_log", "minecraft:birch_log", "minecraft:apple", "goo:widget",
                "minecraft:bread"), leftovers);
    }

    /**
     * The template answer carries the uncovered items and no tag list.
     */
    @Test
    void templateAnswerCarriesNoTagList() {
        BaseValuesModel model = BaseValuesModel.parse("{\"_constants\": {}, \"_groups\": {}}");
        JsonObject answer = ValuesTemplate.answer(model, LOADOUT, "base_values.json");
        assertFalse(answer.has("ungroupedTags"), answer.keySet().toString());
        assertEquals(6, answer.getAsJsonArray("uncoveredItems").size());
    }
}
