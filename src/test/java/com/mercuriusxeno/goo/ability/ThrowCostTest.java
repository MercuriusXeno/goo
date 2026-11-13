package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.IGooValueLookup;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A cost that is no amount of 0 or more refuses at load naming the cost, and an item cost
 * prices the item in the ability's own goo type (decisions flat-cost-per-throw,
 * goo-is-an-int-and-only-the-remainder-is-a-fraction).
 */
class ThrowCostTest {

    private static final String ABILITIES = "data/goo/goo_abilities/";
    private static final String LURKER = ABILITIES + "unstable_lurker.json";

    private static JsonElement read(String resource) throws IOException {
        try (InputStream in = ThrowCostTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"formula\": \"quadratic\", \"baseCost\": 1000}", "-1", "\"cheap\""})
    void costThatIsNoAmountRefusesNamingIt(String cost) throws IOException {
        JsonElement json = read(LURKER);
        json.getAsJsonObject().add("cost", JsonParser.parseString(cost));

        DataResult<AbilityDefinition> parsed = AbilityDefinition.codecFor(AbilityJson.idOfResource(LURKER))
                .parse(JsonOps.INSTANCE, json);

        assertTrue(parsed.error().isPresent(), "an ability costing " + cost + " loaded");
        String message = parsed.error().get().message();
        String named = JsonParser.parseString(cost).isJsonPrimitive()
                ? JsonParser.parseString(cost).getAsString() : "quadratic";
        assertTrue(message.contains(named), message);
    }

    /**
     * A cost naming an item prices it at the item's value in the ability's own goo type
     * (decision ability-cost-builds-as-a-one-type-by-type-value).
     */
    @Nested
    class ItemPriced {

        private static final Identifier COBBLESTONE = Identifier.withDefaultNamespace("cobblestone");
        private static final int COBBLESTONE_MB = 250;

        private static AbilityDefinition lurkerCosting(String cost) throws IOException {
            JsonElement json = read(LURKER);
            json.getAsJsonObject().addProperty("cost", cost);
            return AbilityDefinition.codecFor(AbilityJson.idOfResource(LURKER))
                    .parse(JsonOps.INSTANCE, json).getOrThrow();
        }

        private static IGooValueLookup valuingCobblestone(GooValue value) {
            IGooValueLookup values = mock(IGooValueLookup.class);
            when(values.lookup(COBBLESTONE)).thenReturn(value);
            return values;
        }

        @Test
        void anItemHoldingNoneOfTheAbilitysTypeChargesZero() throws IOException {
            AbilityDefinition lurker = lurkerCosting("3 * minecraft:cobblestone");
            IGooValueLookup values = valuingCobblestone(new GooValue(Map.of(GooTypes.ROCK, COBBLESTONE_MB)));

            assertEquals(0, lurker.cost().in(values));
        }
    }
}
