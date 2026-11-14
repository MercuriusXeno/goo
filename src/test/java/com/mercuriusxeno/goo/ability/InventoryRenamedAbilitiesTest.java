package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each ability renamed in the inventory pass ships under the key its new shown name spells, with its lang entry and
 * icon, and its old key ships nothing.
 * decision ten-abilities-take-their-inventory-names-and-keys
 * decision rename-rekeys-everything-that-names-the-ability
 */
class InventoryRenamedAbilitiesTest {

    private static final String EN_US = "/assets/goo/lang/en_us.json";
    private static final String ABILITY_JSON = "/data/goo/goo_abilities/%s.json";
    private static final String ABILITY_ICON = "/assets/goo/textures/goo/ability/%s.png";

    private static JsonObject lang;

    @BeforeAll
    static void readLang() throws IOException {
        lang = readJson(EN_US);
    }

    @ParameterizedTest
    @MethodSource("renamedAbilities")
    void renamedAbilityShipsUnderItsShownName(String id, String shownName) throws IOException {
        JsonObject ability = readJson(ABILITY_JSON.formatted(id));
        String displayName = ability.get("displayName").getAsString();
        assertEquals("goo.ability." + id.replaceFirst("_", "."), displayName, id);
        assertEquals(shownName, lang.get(displayName).getAsString(), displayName);
        assertNotNull(lang.get(displayName + ".description"), displayName + ".description");
        assertNotNull(InventoryRenamedAbilitiesTest.class.getResource(ABILITY_ICON.formatted(id)), id + " icon");
    }

    static Stream<Arguments> renamedAbilities() {
        return Stream.of(
                Arguments.of("ender_seal", "Seal"),
                Arguments.of("void_zone", "Zone"),
                Arguments.of("quantum_phase_self", "Phase Self"),
                Arguments.of("quantum_phase_other", "Phase Other"),
                Arguments.of("blaze_molten", "Molten"),
                Arguments.of("crystal_shatter", "Shatter"),
                Arguments.of("crystal_glimmer", "Glimmer"),
                Arguments.of("jelly_nectar", "Nectar"),
                Arguments.of("void_cancel", "Cancel"),
                Arguments.of("zoo_kin", "Kin"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ender_zone", "quantum_phase", "quantum_phase_shift"})
    void oldKeyShipsNoAbility(String oldId) {
        assertNull(InventoryRenamedAbilitiesTest.class.getResource(ABILITY_JSON.formatted(oldId)), oldId + " json");
        assertNull(InventoryRenamedAbilitiesTest.class.getResource(ABILITY_ICON.formatted(oldId)), oldId + " icon");
        assertNull(lang.get("goo.ability." + oldId.replaceFirst("_", ".")), oldId + " lang");
    }

    /** A type whose ability was renamed ships the new keys beside its others, and no file under an old key. */
    @ParameterizedTest
    @MethodSource("renamedTypes")
    void renamedTypeShipsItsNewKeysAlone(String type, Set<String> shipped) {
        Set<String> found = AbilityJson.files().stream().map(Path::getFileName).map(Path::toString)
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .filter(id -> id.startsWith(type + "_")).collect(Collectors.toSet());
        assertEquals(shipped, found, type);
    }

    static Stream<Arguments> renamedTypes() {
        return Stream.of(
                Arguments.of("blaze", Set.of("blaze_flames", "blaze_ignite", "blaze_kindle", "blaze_molten")),
                Arguments.of("crystal", Set.of("crystal_glimmer", "crystal_knives", "crystal_prism", "crystal_scales",
                        "crystal_shatter")),
                Arguments.of("jelly", Set.of("jelly_gluttony", "jelly_heal", "jelly_nectar", "jelly_reserve")),
                Arguments.of("void", Set.of("void_cancel", "void_hole", "void_vacuum", "void_zone")),
                Arguments.of("zoo", Set.of("zoo_allure", "zoo_kin", "zoo_rally", "zoo_shape", "zoo_spawn")));
    }

    @Test
    void zoneCarriesTheChanneledPocketItCarriedBeforeTheRename() throws IOException {
        JsonObject zone = readJson(ABILITY_JSON.formatted("void_zone"));
        assertEquals("void", zone.get("gooType").getAsString());
        assertEquals("channeled", zone.get("badge").getAsString());
        assertEquals("goo.ability.void.zone.cost_mechanics", zone.get("cost_mechanics").getAsString());
        JsonObject step = zone.getAsJsonArray("behaviors").get(0).getAsJsonObject();
        assertEquals("zone", step.get("type").getAsString());
        assertEquals(20, step.get("charge").getAsInt());
        assertEquals(6, step.get("enter").getAsInt());
        assertEquals(20, step.get("ring_ticks").getAsInt());
        assertEquals(10, step.get("cell_cost").getAsInt());
        assertEquals(1, step.get("distance_cost").getAsInt());
    }

    @Test
    void sealCarriesTheCurseItCarriedBeforeTheRename() throws IOException {
        JsonObject seal = readJson(ABILITY_JSON.formatted("ender_seal"));
        String steps = seal.getAsJsonArray("behaviors").toString();
        assertEquals("ender", seal.get("gooType").getAsString());
        assertTrue(steps.contains("{\"type\":\"seal\",\"radius\":6,\"range\":32}"), steps);
    }

    private static JsonObject readJson(String resource) throws IOException {
        try (InputStream in = InventoryRenamedAbilitiesTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, resource);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
