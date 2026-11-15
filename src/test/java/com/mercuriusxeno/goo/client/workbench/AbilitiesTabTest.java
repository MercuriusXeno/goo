package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.workbench.AbilitiesModel;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Abilities tab sections its abilities by goo type in the model's type order, each section's abilities as the
 * radial fan sorts them, badge tier then order field, and every section starts open
 * (decisions abilities-tab-sections-by-type-in-picker-order-fan-order-inside,
 * type-sections-fold-by-the-audit-chevron-and-start-expanded).
 */
class AbilitiesTabTest {
    // The rock abilities come first in the file and out of fan order; the model's type order puts unstable first.
    static final String MODEL = """
            {"typeOrder": ["unstable", "rock"], "abilities": [
              {"id": "goo:rock_world", "gooType": "rock", "displayName": "rock_world", "draft": false,
               "badge": "world", "order": 10, "leaves": []},
              {"id": "goo:rock_mob", "gooType": "rock", "displayName": "rock_mob", "draft": false,
               "badge": "mob", "order": 30, "leaves": []},
              {"id": "goo:rock_channeled", "gooType": "rock", "displayName": "rock_channeled", "draft": false,
               "badge": "channeled", "order": 50, "leaves": []},
              {"id": "goo:rock_mob_first", "gooType": "rock", "displayName": "rock_mob_first", "draft": false,
               "badge": "mob", "order": 20, "leaves": []},
              {"id": "goo:unstable_world", "gooType": "unstable", "displayName": "unstable_world", "draft": false,
               "badge": "world", "order": 10, "leaves": []}
            ]}
            """;

    private static AbilitiesTab tab() {
        AbilitiesTab tab = new AbilitiesTab();
        tab.readModel(MODEL);
        return tab;
    }

    @Test
    void sectionsStandInTheModelsTypeOrder() {
        assertEquals(List.of("unstable", "rock"),
                tab().sections(key -> key, "").stream().map(AbilitiesTab.Section::type).toList());
    }

    @Test
    void eachSectionsAbilitiesStandInFanOrder() {
        AbilitiesTab.Section rock = tab().sections(key -> key, "").get(1);
        assertEquals(List.of("goo:rock_channeled", "goo:rock_mob_first", "goo:rock_mob", "goo:rock_world"),
                rock.abilities().stream().map(AbilitiesTab.Ability::id).toList());
    }

    @Test
    void aSearchMatchingNoneOfASectionDropsIt() {
        assertEquals(List.of("rock"),
                tab().sections(key -> key, "mob").stream().map(AbilitiesTab.Section::type).toList());
    }

    /**
     * A rail drop reorders an ability inside its badge tier and rewrites the tier's orders as 10, 20, 30, each an
     * edit the next Save posts; a drop across tiers changes nothing
     * (decision abilities-reorder-by-a-drag-rail-and-the-order-field-hides).
     */
    @org.junit.jupiter.api.Nested
    class Reorder {
        private static final String TIERS = """
                {"typeOrder": ["rock"], "abilities": [
                  %s, %s, %s, %s, %s]}
                """.formatted(ability("c1", "channeled", 10), ability("c2", "channeled", 20),
                ability("c3", "channeled", 30), ability("m1", "mob", 10), ability("m2", "mob", 20));

        private static String ability(String name, String badge, int order) {
            return "{\"id\": \"goo:" + name + "\", \"gooType\": \"rock\", \"displayName\": \"" + name + "\","
                    + " \"draft\": false, \"badge\": \"" + badge + "\", \"order\": " + order + ", \"leaves\": []}";
        }

        private AbilitiesTab tiers() {
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(TIERS);
            return tab;
        }

        private static List<String> ids(AbilitiesTab tab) {
            return tab.sections(key -> key, "").getFirst().abilities().stream().map(AbilitiesTab.Ability::id)
                    .toList();
        }

        @Test
        void movingTheThirdOfATierFirstRewritesItsOrdersAndLeavesTheOtherTier() {
            AbilitiesTab tab = tiers();
            tab.move("rock", 2, 0);
            assertEquals(List.of("goo:c3", "goo:c1", "goo:c2", "goo:m1", "goo:m2"), ids(tab));
            com.google.gson.JsonObject posted = com.google.gson.JsonParser.parseString(tab.postedText())
                    .getAsJsonObject().getAsJsonObject("abilities");
            assertEquals(10, posted.getAsJsonObject("goo:c3").get("order").getAsInt());
            assertEquals(20, posted.getAsJsonObject("goo:c1").get("order").getAsInt());
            assertEquals(30, posted.getAsJsonObject("goo:c2").get("order").getAsInt());
            assertFalse(posted.has("goo:m1") || posted.has("goo:m2"), posted.toString());
        }

        @Test
        void aDropAcrossTiersChangesNothing() {
            AbilitiesTab tab = tiers();
            tab.move("rock", 0, 3);
            assertEquals(List.of("goo:c1", "goo:c2", "goo:c3", "goo:m1", "goo:m2"), ids(tab));
            assertFalse(tab.edited(), tab.postedText());
        }
    }

    /**
     * An ability paying by each thing it does lists each of its unit prices among its cost fields: its cost for the
     * first unit and each step's own price field for any other
     * (decisions decay-pays-per-block-degraded-and-bite, abilities-tab-lists-every-numeric-param-as-expression-boxes).
     */
    @org.junit.jupiter.api.Nested
    class UnitPrices {

        // The price fields the tab lists for one shipped ability file, by field name.
        private static List<String> priceFieldsOf(String name) {
            Identifier id = AbilityJson.idOf(name + ".json");
            JsonObject file = JsonParser.parseString(AbilityJson.read(name)).getAsJsonObject();
            AbilitiesTab tab = new AbilitiesTab();
            tab.readModel(AbilitiesModel.model(Map.of(id, file), Map.of()).toString());
            return tab.abilities().getFirst().prices().stream().map(AbilitiesTab.Leaf::field).toList();
        }

        @Test
        void decaysBlockPriceAndBitePriceAreItsCostFields() {
            assertEquals(List.of("cost", "hit_cost"), priceFieldsOf("nether_decay"));
        }

        /** petrify-pays-per-block-calcified-and-gauge-point */
        @Test
        void petrifysBlockPriceAndGaugePriceAreItsCostFields() {
            assertEquals(List.of("cost", "point_cost"), priceFieldsOf("rock_petrify"));
        }
    }

    @Test
    void everySectionStartsOpenAndToggles() {
        AbilitiesTab tab = tab();
        assertTrue(tab.open("rock"));
        tab.toggle("rock");
        assertFalse(tab.open("rock"));
        assertTrue(tab.open("unstable"));
    }
}
