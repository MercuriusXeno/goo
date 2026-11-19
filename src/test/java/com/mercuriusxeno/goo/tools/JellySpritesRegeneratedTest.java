package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests that jelly's committed fluid and blob sprites, read through the classpath, are the
 * strips generateTextures renders from jelly's current goo_fluid_types palette.
 */
class JellySpritesRegeneratedTest {

    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final String TEXTURES = "/assets/goo/textures/";

    // jelly-fluid-and-blobs-move-to-the-icons-colors
    @Test
    void jellyFluidIsRenderedFromItsPalette() throws IOException {
        assertSameStrip(FluidTextureGenerator.renderFluidStrip(jelly()), readImage("fluid/jelly_fluid.png"));
    }

    // jelly-fluid-and-blobs-move-to-the-icons-colors
    @ParameterizedTest
    @CsvSource({"goo_mask_tiny, jelly_goo_tiny", "goo_mask_small, jelly_goo_small",
            "goo_mask, jelly_goo_base", "goo_mask_large, jelly_goo_large"})
    void jellyBlobIsRenderedFromItsPalette(String maskName, String spriteName) throws IOException {
        FluidTextureGenerator.GooFluidType jelly = jelly();
        float[][] mask = TextureColorShader.luminanceMask(readImage("item/" + maskName + ".png"));
        BufferedImage rendered = GooTextureRenderer.renderGooStripMasked(
                GooTextureRenderer.maskedHeat(jelly), jelly, mask);
        assertSameStrip(rendered, readImage("item/" + spriteName + ".png"));
    }

    private static void assertSameStrip(BufferedImage rendered, BufferedImage committed) {
        assertEquals(rendered.getWidth(), committed.getWidth());
        assertEquals(rendered.getHeight(), committed.getHeight());
        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                assertEquals(Integer.toHexString(rendered.getRGB(x, y)), Integer.toHexString(committed.getRGB(x, y)),
                        "the committed sprite differs from the current palette's render at " + x + "," + y);
            }
        }
    }

    private static FluidTextureGenerator.GooFluidType jelly() throws IOException {
        try (InputStream in = JellySpritesRegeneratedTest.class.getResourceAsStream(FLUID_TYPES_JSON)) {
            assertNotNull(in, "missing on the classpath: " + FLUID_TYPES_JSON);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FluidTextureGenerator.parseFluidTypes(json).stream()
                    .filter(type -> "jelly".equals(type.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("jelly has no goo_fluid_types entry"));
        }
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream in = JellySpritesRegeneratedTest.class.getResourceAsStream(TEXTURES + path)) {
            assertNotNull(in, "missing on the classpath: " + TEXTURES + path);
            return ImageIO.read(in);
        }
    }
}
