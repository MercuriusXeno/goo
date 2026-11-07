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

        /**
         * The order field reorders by the heading's rail and takes no box
         * (decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides).
         */
        @Test
        void theOrderFieldTakesNoLeaf() {
            JsonObject ordered = sample();
            ordered.addProperty("order", 30);
            assertTrue(AbilitiesModel.leaves(SAMPLE, ordered).stream().noneMatch(leaf -> leaf.path().equals("order")));
        }

        /**
         * The model carries the file's icon and description key, the description reading its displayName key plus
         * .description where the file names none (decision ability-json-carries-a-description).
         */
        @Test
        void eachAbilityCarriesItsIconAndDescriptionKeys() {
            JsonObject named = sample();
            named.addProperty("icon", "goo:textures/goo/ability/custom.png");
            named.addProperty("description", "goo.ability.kinetic.sample.told");
            JsonObject ability = AbilitiesModel.model(Map.of(SAMPLE, named), Map.of())
                    .getAsJsonArray("abilities").get(0).getAsJsonObject();
            assertEquals("goo:textures/goo/ability/custom.png", ability.get("icon").getAsString());
            assertEquals("goo.ability.kinetic.sample.told", ability.get("description").getAsString());
            JsonObject defaulted = AbilitiesModel.model(Map.of(SAMPLE, sample()), Map.of())
                    .getAsJsonArray("abilities").get(0).getAsJsonObject();
            assertEquals("", defaulted.get("icon").getAsString());
            assertEquals("goo.ability.kinetic.sample.description", defaulted.get("description").getAsString());
        }

        @Test
        void theModelCarriesThePickersTypeOrder() {
            List<String> carried = AbilitiesModel.model(Map.of(), Map.of()).getAsJsonArray("typeOrder").asList()
                    .stream().map(JsonElement::getAsString).toList();
            assertEquals(GooTypes.order().stream().map(GooTypes::id).toList(), carried);
        }
    }

    /**
     * Each leaf names its mechanism, the step type holding it, the delivery's kind, or its own root field, and its
     * field, its last segment, which its label and description read through
     * (decision ability-params-read-named-under-their-mechanism-and-hover-a-description).
     */
    @Nested
    class Mechanisms {
        private Map<String, AbilitiesModel.Leaf> byPath() {
            JsonObject delivered = sample();
            delivered.getAsJsonObject("delivery").addProperty("ticks_per_charge", 20);
            return AbilitiesModel.leaves(SAMPLE, delivered).stream()
                    .collect(Collectors.toMap(AbilitiesModel.Leaf::path, leaf -> leaf));
        }

        @Test
        void aStepsLeafReadsItsStepTypeAndField() {
            AbilitiesModel.Leaf range = byPath().get("behaviors/1/range");
            assertEquals("grab", range.mechanism());
            assertEquals("range", range.field());
            assertEquals("damage", byPath().get("behaviors/0/amount").mechanism());
        }

        @Test
        void aDeliveryLeafReadsTheDeliverysKind() {
            AbilitiesModel.Leaf ticks = byPath().get("delivery/ticks_per_charge");
            assertEquals("self", ticks.mechanism());
            assertEquals("ticks_per_charge", ticks.field());
        }

        @Test
        void aRootLeafReadsItsOwnField() {
            assertEquals("cost", byPath().get("cost").mechanism());
            assertEquals("cost", byPath().get("cost").field());
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
