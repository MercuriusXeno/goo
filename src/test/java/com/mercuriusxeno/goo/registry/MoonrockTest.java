package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Moonrock registers as an item named Moonrock, its item model draws a sixteen texel sprite on the classpath, and the
 * shipped base values load a value for it carrying lunar's astral.
 * decision lunar-stone-carries-one-sixth-gravity-while-held
 */
class MoonrockTest {

    private static final Identifier MOONROCK = Identifier.fromNamespaceAndPath("goo", "moonrock");
    private static final int SPRITE_SIZE = 16;

    private static InputStream resource(String path) {
        InputStream stream = MoonrockTest.class.getResourceAsStream(path);
        assertNotNull(stream, path + " is not on the classpath");
        return stream;
    }

    private static JsonObject resourceJson(String path) throws IOException {
        try (InputStream stream = resource(path)) {
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void moonrockRegistersAsAnItem() {
        assertTrue(GooItems.ITEMS.getEntries().stream().map(DeferredHolder::getId).anyMatch(MOONROCK::equals),
                "goo:moonrock is no registered item");
    }

    @Test
    void moonrockIsNamedMoonrock() throws IOException {
        assertEquals("Moonrock", resourceJson("/assets/goo/lang/en_us.json").get("item.goo.moonrock").getAsString());
    }

    @Test
    void moonrocksModelDrawsASixteenTexelSprite() throws IOException {
        String model = resourceJson("/assets/goo/items/moonrock.json").getAsJsonObject("model").get("model")
                .getAsString();
        String texture = resourceJson("/assets/goo/models/" + model.substring("goo:".length()) + ".json")
                .getAsJsonObject("textures").get("layer0").getAsString();
        try (InputStream stream = resource("/assets/goo/textures/" + texture.substring("goo:".length()) + ".png")) {
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, texture + " is no image");
            assertEquals(SPRITE_SIZE, image.getWidth(), texture);
            assertEquals(SPRITE_SIZE, image.getHeight(), texture);
        }
    }

    @Test
    void theShippedBaseValuesLoadAValueCarryingAstral() throws IOException {
        String baseValues;
        try (InputStream stream = resource("/data/goo/goo_values/base_values.json")) {
            baseValues = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        GooValue value = GooBaseValuesPreview.load(baseValues).baseValues().get(MOONROCK);

        assertNotNull(value, "base_values.json values no goo:moonrock");
        assertTrue(value.exact(GooTypes.ASTRAL).isPositive(), "goo:moonrock carries no astral: " + value);
    }
}
