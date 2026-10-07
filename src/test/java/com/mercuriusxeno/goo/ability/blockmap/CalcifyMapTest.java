package com.mercuriusxeno.goo.ability.blockmap;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped calcify map holds the main sequence the operator settled,
 * sand, dirt, coarse dirt, gravel, cobblestone, stone, each block stepping
 * one rung and stone the end (decision petrify-stone-encasement-and-calcify-map).
 * The file is read off the classpath as plain JSON, so no registry stands.
 */
class CalcifyMapTest {

    private static final String CALCIFY = "/data/goo/block_maps/calcify.json";
    private static final String STEPS = "steps";
    private static final List<String> MAIN_SEQUENCE = List.of("minecraft:sand", "minecraft:dirt",
            "minecraft:coarse_dirt", "minecraft:gravel", "minecraft:cobblestone", "minecraft:stone");

    private static JsonObject steps() throws IOException {
        try (InputStream in = CalcifyMapTest.class.getResourceAsStream(CALCIFY)) {
            assertNotNull(in, CALCIFY);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject(STEPS);
        }
    }

    @Test
    void sandStepsRungByRungToStone() throws IOException {
        JsonObject steps = steps();
        List<String> walked = new ArrayList<>(List.of(MAIN_SEQUENCE.getFirst()));
        while (steps.has(walked.getLast())) {
            walked.add(steps.get(walked.getLast()).getAsString());
        }
        assertEquals(MAIN_SEQUENCE, walked);
    }

    @Test
    void stoneIsTheEnd() throws IOException {
        assertFalse(steps().has(MAIN_SEQUENCE.getLast()));
    }
}
