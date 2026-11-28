package com.mercuriusxeno.goo.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that nether's, void's, sculk's and astral's committed fluid and goo ball sprites, read through the
 * classpath, are the strips generateTextures renders from each type's current goo_fluid_types entry.
 */
class DarkGooSpritesRegeneratedTest {

    private static final List<String> DARK_TYPES = DarkGooResources.DARK_TYPES;
    private static final List<List<String>> MASKS_AND_SUFFIXES = List.of(List.of("goo_mask_tiny", "_goo_tiny"),
            List.of("goo_mask_small", "_goo_small"), List.of("goo_mask", "_goo_base"), List.of("goo_mask_large", "_goo_large"));

    // dark-goos-climb-to-a-second-hue
    // dark-goos-churn-as-quantum-does
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void fluidIsRenderedFromItsEntry(String type) throws IOException {
        assertSameStrip(FluidTextureGenerator.renderFluidStrip(DarkGooResources.fluidType(type)),
                DarkGooResources.texture("fluid/" + type + "_fluid.png"), type + "_fluid");
    }

    // dark-goos-climb-to-a-second-hue
    // dark-goos-churn-as-quantum-does
    @ParameterizedTest
    @MethodSource("gooBalls")
    void gooBallIsRenderedFromItsEntry(String type, String maskName, String suffix) throws IOException {
        FluidTextureGenerator.GooFluidType entry = DarkGooResources.fluidType(type);
        float[][] mask = TextureColorShader.luminanceMask(DarkGooResources.texture("item/" + maskName + ".png"));
        BufferedImage rendered = GooTextureRenderer.renderGooStripMasked(GooTextureRenderer.maskedHeat(entry), entry, mask);
        assertSameStrip(rendered, DarkGooResources.texture("item/" + type + suffix + ".png"), type + suffix);
    }

    static Stream<Arguments> gooBalls() {
        return DARK_TYPES.stream().flatMap(type -> MASKS_AND_SUFFIXES.stream()
                .map(pair -> Arguments.of(type, pair.get(0), pair.get(1))));
    }

    private static void assertSameStrip(BufferedImage rendered, BufferedImage committed, String sprite) {
        assertEquals(rendered.getWidth(), committed.getWidth(), sprite + " width");
        assertEquals(rendered.getHeight(), committed.getHeight(), sprite + " height");
        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                assertEquals(Integer.toHexString(rendered.getRGB(x, y)), Integer.toHexString(committed.getRGB(x, y)),
                        sprite + " differs from its entry's render at " + x + "," + y);
            }
        }
    }
}
