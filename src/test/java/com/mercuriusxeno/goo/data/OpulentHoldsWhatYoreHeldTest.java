package com.mercuriusxeno.goo.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped base values hold what the values before yore left hold once each yore term moves onto opulent, or
 * drops where opulent already stood beside it, item by item and type by type. The values before are
 * base_values.json as it stood at 25fb0499b, kept as a fixture.
 * decision yore-leaves-and-rewind-moves-to-xeno-as-revert
 */
class OpulentHoldsWhatYoreHeldTest {

    private static final String SHIPPED = "/data/goo/goo_values/base_values.json";
    private static final String BEFORE = "/goo_values/base_values_before_yore_left.json";
    private static final String YORE = "yore";
    private static final String OPULENT = "opulent";
    private static final String YORE_TARGET = "-> yore";
    private static final String OPULENT_TARGET = "-> opulent";

    @Test
    void everyItemHoldsWhatTheOldValuesHoldWithYoreMoved() throws IOException {
        JsonElement before = JsonParser.parseString(read(BEFORE));
        Map<Identifier, GooValue> expected = GooBaseValuesPreview.load(yoreMoved(before).toString()).baseValues();
        Map<Identifier, GooValue> shipped = GooBaseValuesPreview.load(read(SHIPPED)).baseValues();

        assertEquals(expected.keySet(), shipped.keySet());
        List<String> differing = expected.keySet().stream()
                .filter(item -> !expected.get(item).exactAll().equals(shipped.get(item).exactAll()))
                .map(item -> item + " expected " + expected.get(item) + " but ships " + shipped.get(item))
                .toList();
        assertEquals(List.of(), differing);
    }

    /**
     * The values with each yore term on opulent: renamed where its entry holds no opulent, dropped where it does,
     * and each process turning goo into yore turning it into opulent.
     */
    private static JsonElement yoreMoved(JsonElement element) {
        if (element.isJsonObject()) {
            JsonObject entry = element.getAsJsonObject();
            boolean opulentStands = entry.has(OPULENT);
            JsonObject moved = new JsonObject();
            entry.entrySet().forEach(member -> {
                if (!YORE.equals(member.getKey())) {
                    moved.add(member.getKey(), yoreMoved(member.getValue()));
                } else if (!opulentStands) {
                    moved.add(OPULENT, yoreMoved(member.getValue()));
                }
            });
            return moved;
        }
        if (element.isJsonArray()) {
            JsonArray moved = new JsonArray();
            element.getAsJsonArray().forEach(member -> moved.add(yoreMoved(member)));
            return moved;
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(element.getAsString().replace(YORE_TARGET, OPULENT_TARGET));
        }
        return element;
    }

    private static String read(String resource) throws IOException {
        try (InputStream in = OpulentHoldsWhatYoreHeldTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
