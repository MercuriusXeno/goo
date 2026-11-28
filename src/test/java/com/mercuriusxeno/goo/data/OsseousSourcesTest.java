package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that the value registry, seeded from the shipped base values and deriving bone meal and the bone block from
 * their vanilla recipes, gives bone Osseous alone and each skull bone plus its mob's other goo.
 * decision bone-is-osseous-alone-and-skulls-are-bone-plus-mob
 */
class OsseousSourcesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final Map<String, Set<String>> ITEMS_AND_THEIR_GOO_TYPES = Map.of(
            "bone", Set.of("osseous"),
            "bone_meal", Set.of("osseous"),
            "bone_block", Set.of("osseous"),
            "skeleton_skull", Set.of("osseous"),
            "wither_skeleton_skull", Set.of("osseous", "hex"),
            "creeper_head", Set.of("osseous", "unstable"),
            "piglin_head", Set.of("osseous", "nether"),
            "zombie_head", Set.of("zombie"));
    private static GooValueRegistry registry;

    @BeforeAll
    static void deriveTheShippedValues() throws IOException {
        registry = new GooValueRegistry();
        try (InputStream in = OsseousSourcesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES + " is not on the classpath");
            registry.parseBaseValues(in);
        }
        registry.publishBaseAsEffective();
        registry.deriveFromRecipeInputs(List.of(
                recipe("minecraft:bone_meal", 3, slot("minecraft:bone")),
                recipe("minecraft:bone_block", 1, nine("minecraft:bone_meal"))), false);
    }

    @ParameterizedTest
    @MethodSource("boneItems")
    void boneItemCarriesExactlyItsGooTypes(String item) {
        assertEquals(new TreeSet<>(ITEMS_AND_THEIR_GOO_TYPES.get(item)), gooTypesOf(item), item);
    }

    @Test
    void dragonHeadCarriesNoOsseous() {
        Set<String> types = gooTypesOf("dragon_head");
        assertFalse(types.contains("osseous"), "dragon_head carries " + types);
    }

    static Stream<String> boneItems() {
        return ITEMS_AND_THEIR_GOO_TYPES.keySet().stream().sorted();
    }

    private static Set<String> gooTypesOf(String item) {
        GooValue value = registry.table().lookup(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "the registry values no " + item);
        return value.exactAll().entrySet().stream()
                .filter(entry -> entry.getValue().isPositive())
                .map(entry -> GooTypes.id(entry.getKey()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static String[][] nine(String item) {
        String[][] slots = new String[9][];
        Arrays.fill(slots, slot(item));
        return slots;
    }
}
