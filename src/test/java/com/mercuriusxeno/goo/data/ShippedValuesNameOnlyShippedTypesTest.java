package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped base values name only goo types the jar ships a definition for, and every item that held zoo holds no
 * zoo; reads the shipped values through the classpath, derived over no recipe.
 * decision zoo-leaves-and-zombie-goo-replaces-it-as-z
 */
class ShippedValuesNameOnlyShippedTypesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final String TYPES_DIRECTORY = "/data/goo/goo/goo_type";
    private static final String JSON_SUFFIX = ".json";
    private static final String CONSTANTS = "_constants";
    private static final String CONVERSIONS = "_conversions";
    private static final String RESERVED_PREFIX = "_";
    /** A type read off a referent, as in {@code $coral.jelly} or {@code crimson_planks.leaf}. */
    private static final Pattern TYPE_READ = Pattern.compile("[a-z0-9_$]\\.([a-z_]+)");
    /** A conversion formula's source or target type, as in {@code metal / 4 -> osseous / 9}. */
    private static final Pattern FORMULA_TYPE = Pattern.compile("([a-z_]+)\\s*[/*]");
    private static final ResourceKey<GooTypeDefinition> ZOO = GooTypes.bundled("zoo");
    private static Map<Identifier, GooValue> shipped;

    @BeforeAll
    static void deriveShippedBaseValues() throws IOException {
        try (InputStream in = ShippedValuesNameOnlyShippedTypesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            shipped = GooBaseValuesPreview.derive(new String(in.readAllBytes(), StandardCharsets.UTF_8), List.of())
                    .effective();
        }
    }

    /**
     * Read from the file's text, since the loader skips a type it does not know: every amount's key, every
     * {@code .type} read and every conversion formula's type in base_values.json is a shipped type.
     */
    @Test
    void everyTypeTheFileNamesIsShipped() throws IOException, URISyntaxException {
        Set<ResourceKey<GooTypeDefinition>> shippedTypes = shippedTypeKeys();
        List<String> offending = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : baseValuesJson().entrySet()) {
            if (CONSTANTS.equals(entry.getKey())) {
                entry.getValue().getAsJsonObject().entrySet()
                        .forEach(constant -> collectTypes(constant.getKey(), constant.getValue(), offending));
            } else if (CONVERSIONS.equals(entry.getKey())) {
                entry.getValue().getAsJsonObject().entrySet().forEach(conversion -> {
                    Matcher formulaType = FORMULA_TYPE.matcher(conversion.getValue().getAsString());
                    while (formulaType.find()) {
                        offending.add(conversion.getKey() + " names " + formulaType.group(1));
                    }
                });
            } else if (!entry.getKey().startsWith(RESERVED_PREFIX)) {
                collectTypes(entry.getKey(), entry.getValue(), offending);
            }
        }
        offending.removeIf(named -> shippedTypes.contains(GooTypes.bundled(named.substring(named.lastIndexOf(' ') + 1))));
        offending.sort(null);
        assertEquals(List.of(), offending);
    }

    private static void collectTypes(String name, JsonElement value, List<String> named) {
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> amount : value.getAsJsonObject().entrySet()) {
                named.add(name + " names " + amount.getKey());
                collectReads(name, amount.getValue(), named);
            }
        } else {
            collectReads(name, value, named);
        }
    }

    private static void collectReads(String name, JsonElement expression, List<String> named) {
        if (expression.isJsonPrimitive() && expression.getAsJsonPrimitive().isString()) {
            Matcher read = TYPE_READ.matcher(expression.getAsString());
            while (read.find()) {
                named.add(name + " names " + read.group(1));
            }
        }
    }

    private static JsonObject baseValuesJson() throws IOException {
        try (InputStream in = ShippedValuesNameOnlyShippedTypesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES);
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /**
     * Each item that held zoo still loads a value, with no zoo in it.
     */
    @ParameterizedTest
    @MethodSource("itemsThatHeldZoo")
    void itemThatHeldZooHoldsNone(String item) {
        GooValue value = shipped.get(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "base_values.json values no " + item);
        assertTrue(value.exact(ZOO).isZero(), item + ": " + value);
    }

    static Stream<String> itemsThatHeldZoo() {
        return Stream.of("allay_spawn_egg", "armadillo_scute", "armadillo_spawn_egg", "axolotl_spawn_egg",
                "bat_spawn_egg", "bee_nest", "bee_spawn_egg", "blaze_spawn_egg", "bogged_spawn_egg", "bone",
                "brain_coral", "brain_coral_block", "brain_coral_fan", "breeze_spawn_egg", "bubble_coral",
                "bubble_coral_block", "bubble_coral_fan", "camel_husk_spawn_egg", "camel_spawn_egg", "cat_spawn_egg",
                "cave_spider_spawn_egg", "chicken_spawn_egg", "cod_spawn_egg", "copper_golem_spawn_egg",
                "cow_spawn_egg", "creaking_spawn_egg", "creeper_head", "creeper_spawn_egg", "donkey_spawn_egg",
                "dragon_egg", "dragon_head", "drowned_spawn_egg", "elder_guardian_spawn_egg",
                "ender_dragon_spawn_egg", "enderman_spawn_egg", "endermite_spawn_egg", "evoker_spawn_egg",
                "fire_coral", "fire_coral_block", "fire_coral_fan", "fox_spawn_egg", "frog_spawn_egg", "frogspawn",
                "ghast_spawn_egg", "glow_squid_spawn_egg", "goat_horn", "goat_spawn_egg", "guardian_spawn_egg",
                "happy_ghast_spawn_egg", "hoglin_spawn_egg", "horn_coral", "horn_coral_block", "horn_coral_fan",
                "horse_spawn_egg", "husk_spawn_egg", "iron_golem_spawn_egg", "llama_spawn_egg",
                "magma_cube_spawn_egg", "mooshroom_spawn_egg", "mule_spawn_egg", "ocelot_spawn_egg",
                "panda_spawn_egg", "parched_spawn_egg", "parrot_spawn_egg", "phantom_spawn_egg", "pig_spawn_egg",
                "piglin_brute_spawn_egg", "piglin_head", "piglin_spawn_egg", "polar_bear_spawn_egg",
                "pufferfish_spawn_egg", "rabbit_foot", "rabbit_spawn_egg", "salmon_spawn_egg", "sheep_spawn_egg",
                "shulker_spawn_egg", "silverfish_spawn_egg", "skeleton_horse_spawn_egg", "skeleton_spawn_egg",
                "slime_spawn_egg", "sniffer_egg", "sniffer_spawn_egg", "snow_golem_spawn_egg", "spider_spawn_egg",
                "squid_spawn_egg", "stray_spawn_egg", "strider_spawn_egg", "tadpole_spawn_egg", "tube_coral",
                "tube_coral_block", "tube_coral_fan", "turtle_egg", "turtle_scute", "vex_spawn_egg",
                "villager_spawn_egg", "warden_spawn_egg", "wet_sponge", "witch_spawn_egg",
                "wither_skeleton_spawn_egg", "wither_spawn_egg", "wolf_spawn_egg", "zoglin_spawn_egg",
                "zombie_horse_spawn_egg", "zombie_nautilus_spawn_egg", "zombie_spawn_egg",
                "zombie_villager_spawn_egg", "zombified_piglin_spawn_egg");
    }

    private static Set<ResourceKey<GooTypeDefinition>> shippedTypeKeys() throws IOException, URISyntaxException {
        URL directory = Objects.requireNonNull(
                ShippedValuesNameOnlyShippedTypesTest.class.getResource(TYPES_DIRECTORY), TYPES_DIRECTORY);
        try (Stream<Path> files = Files.list(Path.of(directory.toURI()))) {
            return Set.copyOf(files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(JSON_SUFFIX))
                    .map(name -> GooTypes.bundled(name.substring(0, name.length() - JSON_SUFFIX.length())))
                    .toList());
        }
    }
}
