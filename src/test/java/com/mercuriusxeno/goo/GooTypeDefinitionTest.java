package com.mercuriusxeno.goo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that every bundled goo type JSON on the classpath decodes through the
 * registry codec, so the datapack registry accepts what the mod ships, and
 * that the light fields decode to the values the enum used to hold.
 */
class GooTypeDefinitionTest {

    private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final int BLAZE_PEAK = 15;
    private static final float BLAZE_SATURATION = 0.5f;
    private static final int PULSE_PEAK = 8;
    private static final float PULSE_SATURATION = 0.6f;
    private static final float UNSTABLE_SATURATION = 0.55f;

    static Stream<ResourceKey<GooTypeDefinition>> bundledKeys() {
        return GooTypes.BUNDLED.stream();
    }

    /**
     * Each bundled JSON decodes to a definition without error.
     */
    @ParameterizedTest
    @MethodSource("bundledKeys")
    void decodesBundledJson(ResourceKey<GooTypeDefinition> key) throws Exception {
        decodeBundled(key);
    }

    /**
     * The light fields carry the values the enum constructor used to name,
     * read on the types whose saturation is not the common 0.5.
     */
    @Test
    void lightFieldsMatchFormerEnumValues() throws Exception {
        assertEquals(new GooTypeDefinition(BLAZE_PEAK, BLAZE_SATURATION), decodeBundled(GooTypes.BLAZE));
        assertEquals(new GooTypeDefinition(PULSE_PEAK, PULSE_SATURATION), decodeBundled(GooTypes.PULSE));
        assertEquals(new GooTypeDefinition(BLAZE_PEAK, UNSTABLE_SATURATION), decodeBundled(GooTypes.UNSTABLE));
    }

    /**
     * A body missing light_level is refused, so a datapack type states its light.
     */
    @Test
    void refusesBodyWithoutLightLevel() {
        JsonElement json = JsonParser.parseString("{\"saturation_fill\": 0.5}");
        assertTrue(GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    /**
     * A light_level past the vanilla ceiling is refused.
     */
    @Test
    void refusesLightPastCeiling() {
        JsonElement json = JsonParser.parseString("{\"light_level\": 16, \"saturation_fill\": 0.5}");
        assertTrue(GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError());
    }

    private static GooTypeDefinition decodeBundled(ResourceKey<GooTypeDefinition> key) throws Exception {
        String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
        try (InputStream in = GooTypeDefinitionTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Bundled JSON missing on classpath: " + path);
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            JsonElement json = JsonParser.parseReader(reader);
            DataResult<GooTypeDefinition> decoded = GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json);
            assertTrue(decoded.isSuccess(), () -> path + " failed to decode: " + decoded.error());
            return decoded.getOrThrow();
        }
    }
}
