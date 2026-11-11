package com.mercuriusxeno.goo.ability.program;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers which shipped abilities name the own-minion filter: Drain's target
 * behavior alone (decision drain-skips-your-rallied-and-charmed-mobs).
 */
class EntityFilterTest {

    private static final String NOT_OWN_MINION = "not_own_minion";
    private static final String DRAIN_FILE = "hex_drain.json";

    @Test
    void drainsTargetBehaviorAloneNamesTheOwnMinionFilter() {
        List<String> naming = AbilityJson.files().stream()
                .filter(file -> namesFilter(read(file)))
                .map(file -> file.getFileName().toString())
                .toList();
        assertEquals(List.of(DRAIN_FILE), naming);
    }

    @Test
    void drainsTargetWhereListHoldsTheOwnMinionFilter() {
        JsonObject target = JsonParser.parseString(AbilityJson.read("hex_drain")).getAsJsonObject()
                .getAsJsonArray("behaviors").get(0).getAsJsonObject();
        assertEquals("target", target.get("type").getAsString());
        assertEquals(List.of("vulnerable", NOT_OWN_MINION),
                target.getAsJsonArray("where").asList().stream().map(JsonElement::getAsString).toList());
    }

    private static boolean namesFilter(String json) {
        return json.contains("\"" + NOT_OWN_MINION + "\"");
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
