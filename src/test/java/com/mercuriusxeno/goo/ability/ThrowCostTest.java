package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every ability prices a throw at the one integer its JSON names, the same at
 * every stack count, and a cost that is not one whole number refuses at load
 * naming the cost (decision flat-cost-per-throw).
 */
class ThrowCostTest {

    private static final String ABILITIES = "data/goo/goo_abilities/";
    private static final String TIMED_BOMB = ABILITIES + "unstable_timed_bomb.json";

    static List<Path> shippedAbilities() {
        return AbilityJson.files();
    }

    private static JsonElement read(String resource) throws IOException {
        try (InputStream in = ThrowCostTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @MethodSource("shippedAbilities")
    void everyThrowPricesAtTheJsonsCost(Path file) throws IOException {
        String resource = ABILITIES + file.getFileName();
        JsonObject json = read(resource).getAsJsonObject();
        // self-effects-trickle-until-ended: a held effect names an upkeep and no cost
        int jsonCost = json.has("cost") ? json.get("cost").getAsInt() : 0;
        AbilityDefinition definition = AbilityJson.decode(file);

        assertEquals(jsonCost, definition.cost(), definition.id().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"formula\": \"quadratic\", \"baseCost\": 1000}", "1000.5", "-1", "\"cheap\""})
    void costThatIsNotOneWholeNumberRefusesNamingIt(String cost) throws IOException {
        JsonElement json = read(TIMED_BOMB);
        json.getAsJsonObject().add("cost", JsonParser.parseString(cost));

        DataResult<AbilityDefinition> parsed = AbilityDefinition.codecFor(AbilityJson.idOfResource(TIMED_BOMB))
                .parse(JsonOps.INSTANCE, json);

        assertTrue(parsed.error().isPresent(), "an ability costing " + cost + " loaded");
        String message = parsed.error().get().message();
        String named = JsonParser.parseString(cost).isJsonPrimitive()
                ? JsonParser.parseString(cost).getAsString() : "quadratic";
        assertTrue(message.contains(named), message);
    }

    @Test
    void wholeNumberCostLoads() throws IOException {
        JsonElement json = read(TIMED_BOMB);
        json.getAsJsonObject().addProperty("cost", 750);

        AbilityDefinition definition = AbilityDefinition.codecFor(AbilityJson.idOfResource(TIMED_BOMB))
                .parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(750, definition.cost());
    }
}
