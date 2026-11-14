package com.mercuriusxeno.goo.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped goo type states an elasticity from 0 to 1, which its codec
 * reads, and the codec refuses one past 1
 * (decision each-goo-type-carries-elasticity-and-viscosity).
 */
class GooTypeElasticityTest {

    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";
    private static final String ELASTICITY = "elasticity";

    @Test
    void everyShippedTypeStatesAnElasticityFromZeroToOne() throws IOException {
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            JsonObject json = shipped(key);
            assertTrue(json.has(ELASTICITY), key.identifier() + " should state an elasticity");
            float elasticity = GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow().elasticity();
            assertTrue(elasticity >= 0f && elasticity <= 1f,
                    key.identifier() + " should state an elasticity from 0 to 1, not " + elasticity);
        }
    }

    @Test
    void anElasticityPastOneIsACodecError() throws IOException {
        JsonObject json = shipped(GooTypes.ZOO);
        json.addProperty(ELASTICITY, 1.5f);

        assertTrue(GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE, json).isError(),
                "an elasticity of 1.5 should not decode");
    }

    private static JsonObject shipped(ResourceKey<GooTypeDefinition> key) throws IOException {
        String path = TYPE_JSON_FORMAT.formatted(key.identifier().getPath());
        try (InputStream in = GooTypeElasticityTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Shipped JSON missing on classpath: " + path);
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
