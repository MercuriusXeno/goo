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

    /** The materials: the two refined ones the operator kept and the four marker-draft families. */
    static final Set<String> MATERIALS = Set.of("glazed", "iridescent", "polished_glass",
            "xeno_runes", "xeno_seam", "framed_panels");
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
        void theMaterialStateHoldsTheSixMaterials() {
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
