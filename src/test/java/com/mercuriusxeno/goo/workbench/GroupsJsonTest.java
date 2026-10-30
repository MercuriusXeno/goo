package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the groups describe reply's tag referents beside its custom groups, over the shipped base_values.json and
 * hand-built models, with an inventory built in memory (decisions
 * groups-lists-custom-groups-and-every-tag-a-valuation-references, tag-referent-lists-the-games-members-and-values-them).
 */
class GroupsJsonTest {
    private static final List<String> SAPLINGS = List.of("minecraft:oak_sapling", "minecraft:acacia_sapling");
    private static final GroupsJson.Inventory INVENTORY = new GroupsJson.Inventory(
            Set.of("minecraft:oak_sapling", "minecraft:acacia_sapling", "minecraft:oak_log"),
            Map.of("minecraft:saplings", SAPLINGS, "minecraft:some_tag", List.of("minecraft:oak_log")),
            List.of(), List.of());

    private static JsonObject rowNamed(JsonArray described, String name) {
        List<JsonObject> rows = rowsNamed(described, name);
        assertEquals(1, rows.size(), described.toString());
        return rows.getFirst();
    }

    private static List<JsonObject> rowsNamed(JsonArray described, String name) {
        return described.asList().stream().map(JsonElement::getAsJsonObject)
                .filter(row -> name.equals(row.get("name").getAsString())).toList();
    }

    private static List<String> memberIds(JsonObject row) {
        return row.getAsJsonArray("members").asList().stream()
                .map(member -> member.getAsJsonObject().get("id").getAsString()).toList();
    }

    @Nested
    class ShippedFile {

        private JsonArray described() throws IOException {
            String text = new String(BaseValuesModelTest.sampleBytes(), StandardCharsets.UTF_8);
            return GroupsJson.described(BaseValuesModel.parse(text), INVENTORY);
        }

        /**
         * #saplings, which no _groups entry holds, answers after every _groups row as a tag referent with the
         * inventory's members and its #saplings base; a _groups row answers kind group.
         */
        @Test
        void saplingsAnswersAsATagReferentAfterTheGroups() throws IOException {
            JsonArray described = described();
            JsonObject saplings = rowNamed(described, "saplings");
            assertEquals("tag", saplings.get("kind").getAsString());
            assertEquals(SAPLINGS, memberIds(saplings));
            assertEquals("$log / 2", saplings.get("base").getAsString());
            JsonObject copper = rowNamed(described, "copper");
            assertEquals("group", copper.get("kind").getAsString());
            List<JsonElement> rows = described.asList();
            int lastGroup = 0;
            for (int index = 0; index < rows.size(); index++) {
                if ("group".equals(rows.get(index).getAsJsonObject().get("kind").getAsString())) {
                    lastGroup = index;
                }
            }
            assertTrue(rows.indexOf(saplings) > lastGroup, described.toString());
        }
    }

    @Nested
    class ReferencesInExpressions {

        /**
         * A #tag named only inside a value expression answers as a tag referent, and a #name _groups holds
         * answers once, as a group, though the file also keys and names it.
         */
        @Test
        void expressionTagAnswersOnceAndAGroupStaysAGroup() {
            BaseValuesModel model = BaseValuesModel.parse("""
                    {
                        "_groups": {
                            "simple_logs": ["oak_log"]
                        },
                        "#simple_logs": "$log",
                        "stick": "#some_tag / 4 + #simple_logs",
                        "_post_conversions": {
                            "charcoal": "#some_tag"
                        }
                    }
                    """);
            JsonArray described = GroupsJson.described(model, INVENTORY);
            JsonObject someTag = rowNamed(described, "some_tag");
            assertEquals("tag", someTag.get("kind").getAsString());
            assertEquals(List.of("minecraft:oak_log"), memberIds(someTag));
            assertEquals("group", rowNamed(described, "simple_logs").get("kind").getAsString());
            assertEquals(2, described.size(), described.toString());
        }
    }
}
