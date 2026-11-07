package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Abilities tab's model lists every numeric leaf of an ability's JSON by path, an expression field marked so and a
 * raw number marked number-only, the cost and upkeep marked as costs, and writes an edit back into the file's own JSON
 * (decision abilities-tab-lists-every-numeric-param-as-expression-boxes).
 */
class AbilitiesModelTest {

    private static final Identifier SAMPLE = Identifier.fromNamespaceAndPath("goo", "sample_grab");
    private static final String SAMPLE_JSON = """
            {
              "gooType": "kinetic",
              "displayName": "goo.ability.kinetic.sample",
              "badge": "channeled",
              "cost": 500,
              "upkeep": 2,
              "delivery": { "kind": "self" },
              "behaviors": [
                { "type": "damage", "amount": 4, "source": "magic" },
                { "type": "grab", "range": 16, "near": 2, "far": 4, "throw_speed": 1.5, "pull_speed": 1 }
              ]
            }
            """;

    private static JsonObject sample() {
        return JsonParser.parseString(SAMPLE_JSON).getAsJsonObject();
    }

    private static Map<String, AbilitiesModel.Kind> kindsByPath() {
        return AbilitiesModel.leaves(SAMPLE, sample()).stream()
                .collect(Collectors.toMap(AbilitiesModel.Leaf::path, AbilitiesModel.Leaf::kind));
    }

    @Nested
    class Leaves {

        @Test
        void theSampleLoadsAsAnAbility() {
            assertEquals(java.util.Optional.empty(), AbilitiesModel.refusal(SAMPLE, sample()));
        }

        @Test
        void costAndUpkeepAreCosts() {
            assertEquals(AbilitiesModel.Kind.COST, kindsByPath().get("cost"));
            assertEquals(AbilitiesModel.Kind.COST, kindsByPath().get("upkeep"));
        }

        @Test
        void aDamageStepsAmountTakesAnExpression() {
            assertEquals(AbilitiesModel.Kind.EXPRESSION, kindsByPath().get("behaviors/0/amount"));
        }

        @Test
        void aGrabStepsRangeTakesANumberAlone() {
            assertEquals(AbilitiesModel.Kind.NUMBER, kindsByPath().get("behaviors/1/range"));
            assertEquals(AbilitiesModel.Kind.NUMBER, kindsByPath().get("behaviors/1/throw_speed"));
        }

        @Test
        void everyNumericLeafIsListedAndNoOther() {
            assertEquals(java.util.Set.of("cost", "upkeep", "behaviors/0/amount", "behaviors/1/range",
                    "behaviors/1/near", "behaviors/1/far", "behaviors/1/throw_speed", "behaviors/1/pull_speed"),
                    kindsByPath().keySet());
        }

        @Test
        void aCostTheFileOmitsReadsZero() {
            JsonObject free = sample();
            free.remove("cost");
            AbilitiesModel.Leaf cost = AbilitiesModel.leaves(SAMPLE, free).stream()
                    .filter(leaf -> leaf.path().equals("cost")).findFirst().orElseThrow();

            assertEquals(new JsonPrimitive(0), cost.value());
        }
    }

    /**
     * The model carries each ability's badge and order and the goo type picker's order, so the client sections the
     * abilities by type and sorts them as the radial fan does with no server
     * (decision abilities-tab-sections-by-type-in-picker-order-fan-order-inside).
     */
    @Nested
    class Sorting {

        @Test
        void eachAbilityCarriesItsBadgeAndOrderZeroWhereTheFileNamesNone() {
            JsonObject ability = AbilitiesModel.model(Map.of(SAMPLE, sample()), Map.of())
                    .getAsJsonArray("abilities").get(0).getAsJsonObject();
            assertEquals("channeled", ability.get("badge").getAsString());
            assertEquals(0, ability.get("order").getAsInt());
        }

        @Test
        void anOrderTheFileNamesIsCarried() {
            JsonObject ordered = sample();
            ordered.addProperty("order", 30);
            JsonObject ability = AbilitiesModel.model(Map.of(SAMPLE, ordered), Map.of())
                    .getAsJsonArray("abilities").get(0).getAsJsonObject();
            assertEquals(30, ability.get("order").getAsInt());
        }

        @Test
        void theModelCarriesThePickersTypeOrder() {
            List<String> carried = AbilitiesModel.model(Map.of(), Map.of()).getAsJsonArray("typeOrder").asList()
                    .stream().map(JsonElement::getAsString).toList();
            assertEquals(GooTypes.order().stream().map(GooTypes::id).toList(), carried);
        }
    }

    @Nested
    class Edits {

        @Test
        void anEditLandsAtItsPathAndLeavesTheRestAsWritten() {
            JsonObject changes = new JsonObject();
            changes.addProperty("cost", "3 * minecraft:cobblestone");
            changes.addProperty("behaviors/1/range", 24);

            JsonObject edited = AbilitiesModel.edited(sample(), changes);

            assertEquals("3 * minecraft:cobblestone", edited.get("cost").getAsString());
            assertEquals(24, edited.getAsJsonArray("behaviors").get(1).getAsJsonObject().get("range").getAsInt());
            assertEquals("magic", edited.getAsJsonArray("behaviors").get(0).getAsJsonObject().get("source")
                    .getAsString());
        }

        @Test
        void anEditTheCodecRefusesNamesWhy() {
            JsonObject changes = new JsonObject();
            changes.addProperty("behaviors/1/range", "health / 2");

            assertTrue(AbilitiesModel.refusal(SAMPLE, AbilitiesModel.edited(sample(), changes)).isPresent());
        }
    }
}
