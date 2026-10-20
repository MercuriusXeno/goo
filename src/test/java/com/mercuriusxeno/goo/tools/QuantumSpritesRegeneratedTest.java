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
 * Tests that quantum's committed fluid and blob sprites, read through the classpath, are the
 * strips generateTextures renders from quantum's current goo_fluid_types palette.
 */
class QuantumSpritesRegeneratedTest {

    private static final String FLUID_TYPES_JSON = "/data/goo/goo_fluid_types.json";
    private static final String TEXTURES = "/assets/goo/textures/";

    // quantum-is-gray-half-white-half-black
    @Test
    void quantumFluidIsRenderedFromItsPalette() throws IOException {
        assertSameStrip(FluidTextureGenerator.renderFluidStrip(quantum()), readImage("fluid/quantum_fluid.png"));
    }

    // quantum-is-gray-half-white-half-black
    @ParameterizedTest
    @CsvSource({"goo_mask_tiny, quantum_goo_tiny", "goo_mask_small, quantum_goo_small",
            "goo_mask, quantum_goo_base", "goo_mask_large, quantum_goo_large"})
    void quantumBlobIsRenderedFromItsPalette(String maskName, String spriteName) throws IOException {
        FluidTextureGenerator.GooFluidType quantum = quantum();
        float[][] mask = TextureColorShader.luminanceMask(readImage("item/" + maskName + ".png"));
        BufferedImage rendered = GooTextureRenderer.renderGooStripMasked(
                GooTextureRenderer.maskedHeat(quantum), quantum, mask);
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

    private static FluidTextureGenerator.GooFluidType quantum() throws IOException {
        try (InputStream in = QuantumSpritesRegeneratedTest.class.getResourceAsStream(FLUID_TYPES_JSON)) {
            assertNotNull(in, "missing on the classpath: " + FLUID_TYPES_JSON);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return FluidTextureGenerator.parseFluidTypes(json).stream()
                    .filter(type -> "quantum".equals(type.id()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("quantum has no goo_fluid_types entry"));
        }
    }

    private static BufferedImage readImage(String path) throws IOException {
        try (InputStream in = QuantumSpritesRegeneratedTest.class.getResourceAsStream(TEXTURES + path)) {
            assertNotNull(in, "missing on the classpath: " + TEXTURES + path);
            return ImageIO.read(in);
        }
    }
}
