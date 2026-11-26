package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deep's shares leave every item for standing types: deepslate is rock, the sculk family is sculk, ocean animals are
 * jelly and ocean dyes ornate, and void's source is its items; reads the shipped base values through the classpath,
 * derived over no recipe so the conversions apply.
 * decision deepslate-is-valued-as-rock
 * decision ocean-items-follow-animals-to-jelly-and-dyes-to-opulent
 * decision void-reaction-leaves-with-deep
 */
class DeepSharesMovedTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final ResourceKey<GooTypeDefinition> DEEP = GooTypes.bundled("deep");
    private static final ResourceKey<GooTypeDefinition> SCULK = GooTypes.bundled("sculk");
    /** The dolphin egg's types before this change, deep among them. */
    private static final Set<String> DOLPHIN_EGG_BEFORE = Set.of("zoo", "ornate", "deep");
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void deriveShippedBaseValues() throws IOException {
        try (InputStream in = DeepSharesMovedTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            shipped = GooBaseValuesPreview.derive(json, List.of()).effective();
        }
    }

    private static GooValue valueOf(String item) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        return value;
    }

    private static Set<String> typesOf(String item) {
        return valueOf(item).exactAll().entrySet().stream().filter(entry -> entry.getValue().isPositive())
                .map(entry -> GooTypes.id(entry.getKey())).collect(Collectors.toCollection(TreeSet::new));
    }

    @ParameterizedTest
    @ValueSource(strings = {"deepslate", "deepslate_iron_ore"})
    void deepslateIsRock(String item) {
        GooValue value = valueOf(item);
        assertTrue(value.exact(GooTypes.ROCK).isPositive() && value.exact(DEEP).isZero(), item + ": " + value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"echo_shard", "sculk_catalyst", "warden_spawn_egg"})
    void theSculkFamilyIsSculk(String item) {
        GooValue value = valueOf(item);
        assertTrue(value.exact(SCULK).isPositive() && value.exact(DEEP).isZero(), item + ": " + value);
    }

    @Test
    void noItemYieldsDeep() {
        List<Identifier> deep = shipped.entrySet().stream().filter(entry -> !entry.getValue().exact(DEEP).isZero())
                .map(Map.Entry::getKey).toList();
        assertEquals(List.of(), deep);
    }

    @ParameterizedTest
    @MethodSource("oceanItems")
    void oceanItemsFollowAnimalsToJellyAndDyesToOrnate(String item, Set<String> types) {
        assertTrue(typesOf(item).containsAll(types), item + " holds " + typesOf(item) + ", wants " + types);
    }

    static Stream<Arguments> oceanItems() {
        return Stream.of(
                Arguments.of("tube_coral", Set.of("jelly")),
                Arguments.of("dead_tube_coral", Set.of("jelly")),
                Arguments.of("dead_tube_coral_block", Set.of("jelly")),
                Arguments.of("nautilus_shell", Set.of("jelly")),
                Arguments.of("sea_pickle", Set.of("ornate", "glow")),
                Arguments.of("heart_of_the_sea", Set.of("ornate", "astral")),
                Arguments.of("wet_sponge", Set.of("yarn")),
                Arguments.of("ink_sac", Set.of("ornate")));
    }

    @Test
    void dolphinEggDropsItsOceanTraitAndGainsNone() {
        Set<String> less = new TreeSet<>(DOLPHIN_EGG_BEFORE);
        less.remove("deep");
        // zoo-leaves-and-zombie-goo-replaces-it-as-z
        less.remove("zoo");
        assertEquals(less, typesOf("dolphin_spawn_egg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"chorus_fruit", "shulker_shell"})
    void voidComesFromItsItems(String item) {
        assertTrue(valueOf(item).exact(GooTypes.VOID).isPositive(), item + ": " + valueOf(item));
    }
}
