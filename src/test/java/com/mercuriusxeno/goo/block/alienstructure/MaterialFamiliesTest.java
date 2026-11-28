package com.mercuriusxeno.goo.block.alienstructure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.lunar.MarkerLook;
import com.mercuriusxeno.goo.lunar.MonolithMarkerBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The black material families stand as looks, never as blocks: six materials are values of the material state on
 * the four alien structure blocks, the pillar holding material beside style and axis; the monolith marker holds three
 * looks; every look mined keeps its state, and the creative tab lists one stack per look.
 * decision alien-looks-are-block-states-never-separate-blocks
 * decision black-runic-material-families-grow-from-the-marker-drafts
 */
class MaterialFamiliesTest {

    /** The six families landed before the new varietals: the two refined ones and the four marker-draft families. */
    static final Set<String> LANDED_BEFORE_VARIETALS = Set.of("glazed", "iridescent", "polished_glass",
            "xeno_runes", "xeno_seam", "framed_panels");
    /** The new black varietals the operator picked from the drafts page. */
    static final Set<String> NEW_VARIETALS = Set.of("smooth", "chiseled", "inlaid", "hex_cells");
    /** Every material: the families landed before and the new varietals. */
    static final Set<String> MATERIALS = java.util.stream.Stream.concat(LANDED_BEFORE_VARIETALS.stream(),
            NEW_VARIETALS.stream()).collect(Collectors.toSet());
    private static final int FEWEST_NEW_VARIETALS = 4;
    /** The marker's looks: its first, and drafts A and D. */
    static final Set<String> MARKER_LOOKS = Set.of("xeno_runes", "polished_glass", "xeno_seam");

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = MaterialFamiliesTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** The state properties a block's loot table copies onto its drop. */
    private static Set<String> copiedStates(String block) throws IOException {
        JsonObject entry = resourceJson("/data/goo/loot_table/blocks/" + block + ".json").getAsJsonArray("pools")
                .get(0).getAsJsonObject().getAsJsonArray("entries").get(0).getAsJsonObject();
        JsonArray functions = entry.getAsJsonArray("functions");
        assertNotNull(functions, block + " drops with no functions");
        JsonArray properties = functions.get(0).getAsJsonObject().getAsJsonArray("properties");
        return properties.asList().stream().map(p -> p.getAsString()).collect(Collectors.toSet());
    }

    @Nested
    class AlienMaterials {

        @Test
        void theMaterialStateHoldsEveryFamily() {
            assertEquals(MATERIALS, Arrays.stream(AlienMaterial.values()).map(AlienMaterial::getSerializedName)
                    .collect(Collectors.toSet()));
        }

        @Test
        void everyAlienBlockHoldsTheMaterialState() {
            assertTrue(AlienMaterialBlock.STATE_PROPERTIES.contains(AlienMaterialBlock.MATERIAL));
            assertTrue(AlienPillarBlock.STATE_PROPERTIES.containsAll(List.of(AlienMaterialBlock.MATERIAL,
                    AlienPillarBlock.STYLE, RotatedPillarBlock.AXIS)), String.valueOf(AlienPillarBlock.STATE_PROPERTIES));
        }

        @ParameterizedTest
        @CsvSource({"alien_stone,material", "alien_tile,material", "alien_glyph,material",
            "alien_pillar,material style"})
        void eachLookMinedKeepsItsState(String block, String states) throws IOException {
            assertEquals(Set.of(states.split(" ")), copiedStates(block));
        }

        @Test
        void theCreativeTabListsOneStackPerLook() {
            int materials = AlienMaterial.values().length;
            assertEquals(materials, Set.copyOf(AlienLooks.materialLooks()).size());
            assertEquals(materials, Set.copyOf(AlienLooks.glyphLooks()).size());
            List<Map<String, String>> pillars = AlienLooks.pillarLooks();
            assertEquals(materials * PillarStyle.values().length, Set.copyOf(pillars).size());
            assertTrue(pillars.stream().allMatch(look -> look.keySet().equals(Set.of(AlienLooks.MATERIAL,
                    AlienLooks.STYLE))), String.valueOf(pillars));
        }
    }

    /**
     * The new black varietals join the families as looks: at least four stand beyond the six landed before them, each
     * on all four blocks with its blockstate entries, its item model case and its own creative stack.
     * decision black-runic-material-families-grow-from-the-marker-drafts
     * decision alien-looks-are-block-states-never-separate-blocks
     */
    @Nested
    class NewVarietals {

        private static Set<String> materialsBeyondTheLanded() {
            return Arrays.stream(AlienMaterial.values()).map(AlienMaterial::getSerializedName)
                    .filter(name -> !LANDED_BEFORE_VARIETALS.contains(name)).collect(Collectors.toSet());
        }

        @Test
        void atLeastFourStandBeyondTheSixLandedBefore() {
            Set<String> beyond = materialsBeyondTheLanded();
            assertTrue(beyond.size() >= FEWEST_NEW_VARIETALS, "only " + beyond + " stand beyond the landed six");
        }

        @ParameterizedTest
        @CsvSource({"alien_stone", "alien_tile", "alien_glyph"})
        void eachNewVarietalHasItsBlockstateAndItemModel(String block) throws IOException {
            JsonObject variants = resourceJson("/assets/goo/blockstates/" + block + ".json").getAsJsonObject("variants");
            String items = resourceJson("/assets/goo/items/" + block + ".json").toString();
            for (String material : materialsBeyondTheLanded()) {
                assertTrue(variants.has("material=" + material), block + " has no blockstate for " + material);
                assertTrue(items.contains("\"when\":\"" + material + "\""), block + " has no item model for " + material);
            }
        }

        @Test
        void eachNewVarietalHasItsPillarBlockstatesAndItemModel() throws IOException {
            JsonObject variants = resourceJson("/assets/goo/blockstates/alien_pillar.json").getAsJsonObject("variants");
            String items = resourceJson("/assets/goo/items/alien_pillar.json").toString();
            for (String material : materialsBeyondTheLanded()) {
                for (PillarStyle style : PillarStyle.values()) {
                    for (String axis : List.of("x", "y", "z")) {
                        String key = "axis=" + axis + ",material=" + material + ",style=" + style.getSerializedName();
                        assertTrue(variants.has(key), "alien_pillar has no blockstate " + key);
                    }
                }
                assertTrue(items.contains("\"when\":\"" + material + "\""), "alien_pillar has no item model for "
                        + material);
            }
        }

        @Test
        void eachNewVarietalHasItsOwnCreativeStack() {
            Set<String> listed = AlienLooks.materialLooks().stream().map(look -> look.get(AlienLooks.MATERIAL))
                    .collect(Collectors.toSet());
            assertTrue(listed.containsAll(materialsBeyondTheLanded()), "the creative tab lists only " + listed);
        }
    }

    @Nested
    class MarkerLooks {

        @Test
        void theMarkerHoldsFourLooksBesideItsAxis() {
            assertEquals(MARKER_LOOKS, Arrays.stream(MarkerLook.values()).map(MarkerLook::getSerializedName)
                    .collect(Collectors.toSet()));
            assertTrue(MonolithMarkerBlock.STATE_PROPERTIES.containsAll(List.of(MonolithMarkerBlock.AXIS,
                    MonolithMarkerBlock.LOOK)));
        }

        @Test
        void aMinedMarkerKeepsItsLook() throws IOException {
            assertEquals(Set.of(MarkerLook.STATE), copiedStates("monolith_marker"));
        }

        @Test
        void theCreativeTabListsOneStackPerLook() {
            assertEquals(MARKER_LOOKS, MarkerLook.looks().stream().map(look -> look.get(MarkerLook.STATE))
                    .collect(Collectors.toSet()));
        }
    }
}
