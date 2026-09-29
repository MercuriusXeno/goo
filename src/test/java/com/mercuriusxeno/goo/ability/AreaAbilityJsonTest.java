package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.ProgressiveAreaStep;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The area abilities' JSONs decode to the stack ceiling and start radius the
 * shape ladder reads (decisions disc-opens-circularly-per-stack,
 * tunnel-stays-3x3-ee-homage, sphere-is-frost-alone), read from the classpath.
 */
class AreaAbilityJsonTest {

    private static final String ABILITIES = "data/goo/goo_abilities/";
    private static final int LADDER_STACKS = 6;

    private static JsonObject read(String ability) throws IOException {
        String resource = ABILITIES + ability + ".json";
        try (InputStream in = AreaAbilityJsonTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static AbilityDefinition decode(String ability, JsonObject json) {
        String resource = ABILITIES + ability + ".json";
        return AbilityDefinition.codecFor(AbilityJson.idOfResource(resource)).parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new IllegalStateException(resource + ": " + message));
    }

    private static AbilityDefinition decode(String ability) throws IOException {
        return decode(ability, read(ability));
    }

    @ParameterizedTest
    @ValueSource(strings = {"rock_flat", "blaze_flat", "frost_flat"})
    void flatDiscStacksSixFromRadiusZero(String ability) throws IOException {
        AbilityDefinition definition = decode(ability);
        ProgressiveAreaStep step = (ProgressiveAreaStep) definition.behaviors().getFirst();

        assertEquals(LADDER_STACKS, definition.chain().maxStacks(), ability + " maxStacks");
        assertEquals(0, step.startRadius(), ability + " start_radius");
    }

    @ParameterizedTest
    @ValueSource(strings = {"rock_tunnel", "blaze_tunnel", "frost_tunnel"})
    void tunnelStacksSix(String ability) throws IOException {
        assertEquals(LADDER_STACKS, decode(ability).chain().maxStacks(), ability + " maxStacks");
    }

    @Test
    void frostBallOpensFromRadiusThreeOneBlockPerStack() throws IOException {
        ProgressiveAreaStep step = (ProgressiveAreaStep) decode("frost_sphere").behaviors().getFirst();

        assertEquals(List.of(3, 4, 5, 6), IntStream.rangeClosed(1, 4).mapToObj(step::radius).toList());
    }

    @Test
    void progressiveAreaReadsTheStartRadiusItsJsonNames() throws IOException {
        JsonObject json = read("rock_flat");
        json.getAsJsonArray("behaviors").get(0).getAsJsonObject().addProperty("start_radius", 2);

        ProgressiveAreaStep step = (ProgressiveAreaStep) decode("rock_flat", json).behaviors().getFirst();

        assertEquals(2, step.startRadius());
    }
}
