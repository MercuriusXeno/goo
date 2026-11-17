package com.mercuriusxeno.goo.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunar palette: one grey ramp, pale rock to blue-black, each color picked from a vanilla block texture, which every
 * lunar sprite draws from alone, with regolith drawing only anorthosite's own colors.
 * decision lunar-blocks-are-repainted-from-one-shared-palette
 * decision lunar-textures-are-repainted-by-hand-from-vanilla-palettes
 */
class LunarPaletteTest {

    /** One palette color and the vanilla block texture, under assets/minecraft/textures/block/, it was picked from. */
    record PaletteColor(int rgb, String vanillaTexture) {
        @Override
        public String toString() {
            return String.format("#%06x from %s", rgb, vanillaTexture);
        }
    }

    /**
     * Pale to dark: anorthosite's body sits at the pale end, regolith's dust in the middle, the dark end below. A landform sprite joining the palette draws from this ramp, or adds a named color beside it.
     */
    static final List<PaletteColor> LUNAR_PALETTE = List.of(
            new PaletteColor(0xe9e9e9, "diorite"),
            new PaletteColor(0xcececf, "diorite"),
            new PaletteColor(0xbebfc1, "diorite"),
            new PaletteColor(0xb0b0b0, "smooth_stone"),
            new PaletteColor(0xa3a3a3, "smooth_stone"),
            new PaletteColor(0x8f8f8f, "stone"),
            new PaletteColor(0x7f7f7f, "stone"),
            new PaletteColor(0x686868, "stone"),
            new PaletteColor(0x515151, "deepslate"),
            new PaletteColor(0x3d3d43, "deepslate"),
            new PaletteColor(0x2f2f37, "deepslate"),
            new PaletteColor(0x1b2632, "smooth_basalt"));

    /**
     * The sprites under assets/goo/textures/block/ held to the palette. Iron core stands outside it on its vanilla-palette
     * repaint until its color is picked on the shared palette.
     * decision iron-core-returns-to-its-vanilla-palette-repaint
     */
    static final List<String> LUNAR_SPRITES = List.of("anorthosite", "regolith");

    private static BufferedImage image(String path) throws IOException {
        try (InputStream stream = LunarPaletteTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path + " is no image");
            return image;
        }
    }

    /**
     * The pixel as the file stores it. getRGB reads a greyscale PNG's grey as linear light and brightens it, so stone's
     * 0x7f reads as 0xbb; a grey pixel is read from the raster instead.
     */
    private static int storedArgb(BufferedImage image, int x, int y) {
        if (image.getColorModel().getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            return image.getRGB(x, y);
        }
        Raster raster = image.getRaster();
        int max = (1 << raster.getSampleModel().getSampleSize(0)) - 1;
        int grey = raster.getSample(x, y, 0) * 0xff / max;
        int alpha = raster.getNumBands() > 1 ? raster.getSample(x, y, 1) * 0xff / max : 0xff;
        return alpha << 24 | grey << 16 | grey << 8 | grey;
    }

    private static Set<Integer> opaqueColorsOf(BufferedImage image) {
        Set<Integer> colors = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                if (argb >>> 24 != 0) {
                    colors.add(argb & 0xffffff);
                }
            }
        }
        return colors;
    }

    private static Set<Integer> lunarSpriteColors(String sprite) throws IOException {
        return opaqueColorsOf(image("/assets/goo/textures/block/" + sprite + ".png"));
    }

    private static String hex(Set<Integer> colors) {
        return colors.stream().map(rgb -> String.format("#%06x", rgb)).collect(Collectors.joining(" "));
    }

    static Stream<String> lunarSprites() {
        return LUNAR_SPRITES.stream();
    }

    static Stream<PaletteColor> lunarPalette() {
        return LUNAR_PALETTE.stream();
    }

    @ParameterizedTest
    @MethodSource("lunarSprites")
    void everyPixelOfTheSpriteIsAPaletteColor(String sprite) throws IOException {
        BufferedImage image = image("/assets/goo/textures/block/" + sprite + ".png");
        Set<Integer> palette = LUNAR_PALETTE.stream().map(PaletteColor::rgb).collect(Collectors.toSet());
        Set<Integer> offPalette = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                if (argb >>> 24 != 0xff || !palette.contains(argb & 0xffffff)) {
                    offPalette.add(argb);
                }
            }
        }
        assertTrue(offPalette.isEmpty(), sprite + " draws off the palette: "
                + offPalette.stream().map(argb -> String.format("#%08x", argb)).collect(Collectors.joining(" ")));
    }

    /** Regolith is anorthosite ground to dust, so it draws no color anorthosite does not. */
    @Test
    void regolithDrawsOnlyAnorthositesColors() throws IOException {
        Set<Integer> regolith = lunarSpriteColors("regolith");
        Set<Integer> foreign = new TreeSet<>(regolith);
        foreign.removeAll(lunarSpriteColors("anorthosite"));
        assertTrue(foreign.isEmpty(), "regolith draws colors anorthosite does not: " + hex(foreign));
    }

    @ParameterizedTest
    @MethodSource("lunarPalette")
    void eachPaletteColorIsDrawnInTheVanillaTextureItNames(PaletteColor color) throws IOException {
        Set<Integer> vanilla = opaqueColorsOf(
                image("/assets/minecraft/textures/block/" + color.vanillaTexture() + ".png"));
        assertTrue(vanilla.contains(color.rgb()), color + ", which draws only " + hex(vanilla));
    }
}
