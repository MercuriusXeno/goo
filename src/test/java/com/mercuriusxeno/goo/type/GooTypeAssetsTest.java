package com.mercuriusxeno.goo.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that every bundled goo type ships the assets a registered type needs:
 * each texture its JSON names, its type icon, its four item models, its
 * goo_fluid_types entry, and its type, potion and brew names in the lang file.
 */
class GooTypeAssetsTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final String LANG_JSON = "/assets/goo/lang/en_us.json";
    private static final String TYPE_ICON_FORMAT = "/assets/goo/textures/goo/type/%s.png";
    private static final String ITEM_MODEL_FORMAT = "/assets/goo/models/item/%s_goo%s.json";
    private static final List<String> ITEM_MODEL_SUFFIXES = List.of("", "_tiny", "_small", "_large");

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }

    @ParameterizedTest
    @MethodSource("bundledKeys")
    void everyNamedTextureShipsOnTheClasspath(ResourceKey<GooTypeDefinition> key) throws IOException {
        GooTypeTextures textures = GooTypeDefinition.CODEC
                .parse(JsonOps.INSTANCE, readJson(TYPE_JSON_FORMAT.formatted(GooTypes.id(key)))).getOrThrow()
                .textures();
        for (Optional<Identifier> texture : List.of(textures.gooTiny(), textures.gooSmall(), textures.gooBase(),
                textures.gooLarge(), textures.fluidStill(), textures.fluidFlowing())) {
            assertTrue(texture.isPresent(), key + " names no texture in one slot");
            Identifier id = texture.get();
            assertShipped("/assets/" + id.getNamespace() + "/textures/" + id.getPath() + ".png");
        }
        assertShipped(TYPE_ICON_FORMAT.formatted(GooTypes.id(key)));
        for (String suffix : ITEM_MODEL_SUFFIXES) {
            assertShipped(ITEM_MODEL_FORMAT.formatted(GooTypes.id(key), suffix));
        }
    }

    @ParameterizedTest
    @MethodSource("bundledKeys")
    void fluidTypesListTheType(ResourceKey<GooTypeDefinition> key) throws IOException {
        String id = GooTypes.id(key);
        boolean listed = StreamSupport.stream(readJson(FLUID_TYPES_JSON).getAsJsonArray().spliterator(), false)
                .anyMatch(entry -> id.equals(entry.getAsJsonObject().get("id").getAsString()));
        assertTrue(listed, id + " has no goo_fluid_types entry");
    }

    @ParameterizedTest
    @MethodSource("bundledKeys")
    void langNamesTheTypeItsPotionAndItsBrew(ResourceKey<GooTypeDefinition> key) throws IOException {
        JsonObject lang = readJson(LANG_JSON).getAsJsonObject();
        String id = GooTypes.id(key);
        for (String langKey : List.of(GooTypeNames.translationKey(key),
                "item.minecraft.potion.effect." + id + "_goo", "effect.goo." + id + "_brew")) {
            assertTrue(lang.has(langKey), "lang lacks " + langKey);
        }
    }

    private static void assertShipped(String path) throws IOException {
        try (InputStream in = GooTypeAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
        }
    }

    private static JsonElement readJson(String path) throws IOException {
        try (InputStream in = GooTypeAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing on the classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader);
            }
        }
    }
}
