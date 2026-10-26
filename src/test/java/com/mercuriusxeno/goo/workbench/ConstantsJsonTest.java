package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the constants evaluate answer's split of Used by into the items naming a constant directly and the terminus
 * items its cascade reaches (decision used-by-draws-the-terminus-items-alone).
 */
class ConstantsJsonTest {
    private static final String FILE = """
            {
                "_constants": {"base": 4, "other": "$base * 2"},
                "_groups": {"logs": ["oak_log", "birch_log"]},
                "_restricted": [],
                "_conversions": {},
                "_post_conversions": {},
                "stick": "$base",
                "#logs": "$base",
                "plank": "$other"
            }
            """;

    private static List<String> ids(JsonObject constant, String field) {
        JsonArray ids = constant.getAsJsonArray(field);
        return ids.asList().stream().map(JsonElement::getAsString).toList();
    }

    /**
     * $base is read by the stick, the #logs group and $other, so direct holds the stick alone and usedBy holds the
     * stick beside the group's members and the plank $other values.
     */
    @Test
    void directHoldsTheItemsNamingTheConstantAlone() {
        JsonObject base = JsonParser.parseString(ConstantsJson.evaluated(BaseValuesModel.parse(FILE), Map.of()))
                .getAsJsonObject().getAsJsonArray("constants").asList().stream().map(JsonElement::getAsJsonObject)
                .filter(constant -> "base".equals(constant.get("name").getAsString())).findFirst().orElseThrow();
        assertEquals(List.of("stick"), ids(base, "direct"));
        List<String> usedBy = ids(base, "usedBy");
        assertTrue(usedBy.containsAll(List.of("stick", "oak_log", "birch_log", "plank")), usedBy.toString());
        assertTrue(usedBy.stream().noneMatch(id -> id.startsWith("#") || "other".equals(id)), usedBy.toString());
    }
}
