package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The warrens' xeno-rich alien blocks register as blocks and items under the mod's id, drop themselves when mined, and
 * carry more xeno than one $lunar does, read from the shipped base values.
 * decision warrens-hold-xeno-rich-alien-blocks
 * decision each-lunar-zone-holds-its-own-deposit-richer-by-depth
 */
class WarrenAlienBlocksTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    /** An item line no shipped item holds, valued at one $lunar, so the loader resolves the constant for the test. */
    private static final Identifier LUNAR_PROBE = Identifier.fromNamespaceAndPath("goo", "lunar_constant_probe");

    private static Identifier goo(String path) {
        return Identifier.fromNamespaceAndPath("goo", path);
    }

    private static Set<Identifier> registeredIds(DeferredRegister<?> register) {
        return register.getEntries().stream().map(DeferredHolder::getId).collect(Collectors.toSet());
    }

    private static String resourceText(String path) throws IOException {
        try (InputStream stream = WarrenAlienBlocksTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The shipped base values, with the probe line valuing one $lunar beside them. */
    private static Map<Identifier, GooValue> baseValuesWithLunarProbe() throws IOException {
        JsonObject root = JsonParser.parseString(resourceText(BASE_VALUES)).getAsJsonObject();
        root.addProperty(LUNAR_PROBE.toString(), "$lunar");
        return GooBaseValuesPreview.load(root.toString()).baseValues();
    }

    private static void assertRegistersAndDropsItself(String block) throws IOException {
        assertTrue(registeredIds(GooBlocks.BLOCKS).contains(goo(block)), "no block answers goo:" + block);
        assertTrue(registeredIds(GooItems.ITEMS).contains(goo(block)), "no item answers goo:" + block);
        JsonObject entry = JsonParser.parseString(resourceText("/data/goo/loot_table/blocks/" + block + ".json"))
                .getAsJsonObject().getAsJsonArray("pools").get(0).getAsJsonObject().getAsJsonArray("entries").get(0)
                .getAsJsonObject();
        assertEquals("goo:" + block, entry.get("name").getAsString());
    }

    private static void assertRicherInXenoThanLunar(String block) throws IOException {
        Map<Identifier, GooValue> values = baseValuesWithLunarProbe();
        GooValue lunar = values.get(LUNAR_PROBE);
        GooValue value = values.get(goo(block));
        assertNotNull(lunar, "the probe line valued no $lunar");
        assertNotNull(value, "base_values.json values no goo:" + block);
        assertTrue(value.exact(GooTypes.XENO).compareTo(lunar.exact(GooTypes.XENO)) > 0,
                "goo:" + block + " " + value + " carries no more xeno than $lunar " + lunar);
    }

    /**
     * The alien structure family, the blocks the warrens' mini structures and the eldritch ground's buildings are built
     * from: at least four members, each its own block and item, dropping itself and richer in xeno than $lunar.
     */
    @Nested
    class AlienStructureFamily {

        /** The family's members, plain, pillar, glyph and tile. */
        static final List<String> MEMBERS = List.of("alien_stone", "alien_pillar", "alien_glyph", "alien_tile");

        @Test
        void theFamilyHoldsAtLeastFourMembers() {
            assertTrue(MEMBERS.size() >= 4 && Set.copyOf(MEMBERS).size() == MEMBERS.size(), String.valueOf(MEMBERS));
        }

        @ParameterizedTest
        @FieldSource("MEMBERS")
        void eachMemberRegistersAsABlockAndAnItemThatDropsItself(String member) throws IOException {
            assertRegistersAndDropsItself(member);
        }

        @ParameterizedTest
        @FieldSource("MEMBERS")
        void eachMemberCarriesMoreXenoThanLunar(String member) throws IOException {
            assertRicherInXenoThanLunar(member);
        }
    }

    @Nested
    class MonolithMarker {

        @Test
        void registersAsABlockAndAnItemThatDropsItself() throws IOException {
            assertRegistersAndDropsItself("monolith_marker");
        }

        @Test
        void carriesMoreXenoThanLunar() throws IOException {
            assertRicherInXenoThanLunar("monolith_marker");
        }
    }
}
