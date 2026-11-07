package com.mercuriusxeno.goo.client.workbench;

import org.junit.jupiter.api.Test;
import java.util.List;
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

    @Test
    void everySectionStartsOpenAndToggles() {
        AbilitiesTab tab = tab();
        assertTrue(tab.open("rock"));
        tab.toggle("rock");
        assertFalse(tab.open("rock"));
        assertTrue(tab.open("unstable"));
    }
}
