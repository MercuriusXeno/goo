package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that Ornate has left: Osseous is the one bundled O type, the shipped base values name no ornate, and the
 * items Ornate held read their new goo once the shipped values derive, the dyes through the #dyes tag and the globe
 * banner pattern, which banner_patterns pinned, through a recipe given it here.
 * decision osseous-replaces-ornate-as-the-o-goo
 * decision shells-take-osseous-and-ornates-share-drops
 * decision flowers-take-leaf-and-dyes-inherit
 * decision oxidation-converts-metal-to-weird
 */
class OrnateLeavesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final Set<Identifier> DYES = Set.of(Identifier.withDefaultNamespace("red_dye"),
            Identifier.withDefaultNamespace("blue_dye"), Identifier.withDefaultNamespace("white_dye"));
    private static final Map<String, Set<String>> REHOMED_ITEMS_AND_THEIR_GOO_TYPES = Map.ofEntries(
            Map.entry("turtle_scute", Set.of("osseous")),
            Map.entry("nautilus_shell", Set.of("osseous", "jelly")),
            Map.entry("turtle_egg", Set.of("osseous")),
            Map.entry("sniffer_egg", Set.of("osseous")),
            Map.entry("poppy", Set.of("leaf")),
            Map.entry("dandelion", Set.of("leaf")),
            Map.entry("oxeye_daisy", Set.of("leaf")),
            Map.entry("red_dye", Set.of("leaf")),
            Map.entry("blue_dye", Set.of("leaf")),
            Map.entry("white_dye", Set.of("leaf")),
            Map.entry("ink_sac", Set.of("void")),
            Map.entry("cow_spawn_egg", Set.of("jelly", "osseous")));
    private static String shippedText;
    private static Map<Identifier, GooValue> derived;

    @BeforeAll
    static void deriveTheShippedValues() throws IOException {
        try (InputStream in = OrnateLeavesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES + " is not on the classpath");
            shippedText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        derived = GooBaseValuesPreview.derive(shippedText, List.of(
                        recipe("minecraft:globe_banner_pattern", 1, slot("minecraft:paper"),
                                slot("minecraft:oxeye_daisy")),
                        recipe("minecraft:copper_ingot", 1, nine("minecraft:copper_nugget")),
                        recipe("minecraft:copper_block", 1, nine("minecraft:copper_ingot"))),
                tag -> "dyes".equals(tag.getPath()) ? DYES : Set.of()).effective();
    }

    @Test
    void osseousIsTheOneBundledTypeStartingWithO() {
        assertEquals(List.of("osseous"),
                GooTypes.BUNDLED.stream().map(GooTypes::id).filter(id -> id.startsWith("o")).toList());
    }

    @Test
    void shippedValuesNameNoOrnate() {
        List<String> naming = shippedText.lines().filter(line -> line.contains("ornate")).map(String::strip).toList();
        assertEquals(List.of(), naming);
    }

    @ParameterizedTest
    @MethodSource("rehomedItems")
    void rehomedItemCarriesExactlyItsNewGooTypes(String item) {
        assertEquals(new TreeSet<>(REHOMED_ITEMS_AND_THEIR_GOO_TYPES.get(item)), gooTypesOf(item), item);
    }

    @Test
    void aPinnedBannerPatternCarriesItsRecipesGoo() {
        Map<ResourceKey<GooTypeDefinition>, GooFraction> recipeSum = new HashMap<>(valueOf("paper").exactAll());
        valueOf("oxeye_daisy").exactAll().forEach((type, amount) -> recipeSum.merge(type, amount, GooFraction::plus));
        recipeSum.values().removeIf(GooFraction::isZero);
        Map<ResourceKey<GooTypeDefinition>, GooFraction> pattern = new HashMap<>(
                valueOf("globe_banner_pattern").exactAll());
        pattern.values().removeIf(GooFraction::isZero);
        assertEquals(recipeSum, pattern);
    }

    @Test
    void exposedCopperCarriesWeird() {
        GooValue exposed = valueOf("exposed_copper");
        assertTrue(exposed.exact(GooTypes.WEIRD).isPositive(), "exposed_copper carries no weird: " + exposed);
    }

    static Stream<String> rehomedItems() {
        return REHOMED_ITEMS_AND_THEIR_GOO_TYPES.keySet().stream().sorted();
    }

    private static GooValue valueOf(String item) {
        GooValue value = derived.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "the derived values hold no " + item);
        return value;
    }

    private static String[][] nine(String item) {
        String[][] slots = new String[9][];
        Arrays.fill(slots, slot(item));
        return slots;
    }

    private static Set<String> gooTypesOf(String item) {
        return valueOf(item).exactAll().entrySet().stream()
                .filter(entry -> entry.getValue().isPositive())
                .map(entry -> GooTypes.id(entry.getKey()))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
