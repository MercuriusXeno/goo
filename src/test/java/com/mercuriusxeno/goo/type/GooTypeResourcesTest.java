package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that every bundled goo type ships the resources the client reads by
 * its id: the four goo sprites and their models, the fluid sprite, the type
 * icon, and the lang names of the type, its potion and its brew.
 */
class GooTypeResourcesTest {

    private static final String ASSETS = "/assets/goo/";
    private static final String LANG_PATH = ASSETS + "lang/en_us.json";
    private static final List<String> GOO_SIZES = List.of("tiny", "small", "base", "large");
    private static JsonObject lang;

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }

    @BeforeAll
    static void readLang() throws IOException {
        try (InputStream in = GooTypeResourcesTest.class.getResourceAsStream(LANG_PATH);
             Reader reader = new InputStreamReader(Objects.requireNonNull(in, LANG_PATH), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    /**
     * Each bundled type's goo sprites, fluid sprite and type icon are on the
     * classpath, each animated sprite beside its mcmeta.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void texturesShipForEveryBundledType(ResourceKey<GooTypeDefinition> key) {
        String id = GooTypes.id(key);
        for (String size : GOO_SIZES) {
            assertResource("textures/item/" + id + "_goo_" + size + ".png");
            assertResource("textures/item/" + id + "_goo_" + size + ".png.mcmeta");
        }
        assertResource("textures/fluid/" + id + "_fluid.png");
        assertResource("textures/fluid/" + id + "_fluid.png.mcmeta");
        assertResource("textures/goo/type/" + id + ".png");
    }

    /**
     * Each bundled type's four goo item models are on the classpath; the
     * base size's model drops the size suffix.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void itemModelsShipForEveryBundledType(ResourceKey<GooTypeDefinition> key) {
        String id = GooTypes.id(key);
        assertResource("models/item/" + id + "_goo.json");
        assertResource("models/item/" + id + "_goo_tiny.json");
        assertResource("models/item/" + id + "_goo_small.json");
        assertResource("models/item/" + id + "_goo_large.json");
    }

    /**
     * Each bundled type names itself, its potion and its brew effect in en_us.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void langNamesEveryBundledType(ResourceKey<GooTypeDefinition> key) {
        String id = GooTypes.id(key);
        for (String langKey : List.of(GooTypeNames.translationKey(key),
                "item.minecraft.potion.effect." + id + "_goo", "effect.goo." + id + "_brew")) {
            assertTrue(lang.has(langKey), "en_us lacks " + langKey);
        }
    }

    private static void assertResource(String path) {
        URL resource = GooTypeResourcesTest.class.getResource(ASSETS + path);
        assertNotNull(resource, "missing on classpath: " + ASSETS + path);
    }
}
