package com.mercuriusxeno.goo.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.registry.SpritePixels.gooBlock;
import static com.mercuriusxeno.goo.registry.SpritePixels.image;
import static com.mercuriusxeno.goo.registry.SpritePixels.storedArgb;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunar palette: clay's cool blue-grey for the ground over deepslate's dark greys for the iron core, each color
 * picked from a vanilla block texture, which every lunar sprite draws from alone, with regolith drawing only
 * anorthosite's own colors.
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
     * Pale to dark, the colors the operator picked from drafts: the four ground tones anorthosite and regolith draw, then
     * the iron core's seven. A landform sprite joining the palette draws from this ramp, or adds a named color beside it.
     * decision lunar-textures-get-form-and-texture-before-color
     */
    static final List<PaletteColor> LUNAR_PALETTE = List.of(
            new PaletteColor(0xacaebd, "clay"),
            new PaletteColor(0xa1a7b1, "clay"),
            new PaletteColor(0x9ca1ac, "clay"),
            new PaletteColor(0x9499a4, "clay"),
            new PaletteColor(0x7a7a7a, "cobbled_deepslate"),
            new PaletteColor(0x696969, "cobbled_deepslate"),
            new PaletteColor(0x5a5a5a, "cobbled_deepslate"),
            new PaletteColor(0x4a4a4f, "cobbled_deepslate"),
            new PaletteColor(0x3f3f45, "cobbled_deepslate"),
            new PaletteColor(0x353539, "cobbled_deepslate"),
            new PaletteColor(0x2f2f37, "deepslate"));

    /**
     * A ground block's sprites: its own and the variants its blockstate picks among per block, sharing one border band.
     * decision lunar-repaints-are-picked-from-drafts-on-a-page
     */
    static List<String> variantsOf(String block) {
        return List.of(block, block + "_2", block + "_3", block + "_4");
    }

    /** The sprites under assets/goo/textures/block/ held to the palette: both ground blocks' variants and the iron core. */
    static final List<String> LUNAR_SPRITES = Stream.concat(Stream.of("anorthosite", "regolith")
            .flatMap(block -> variantsOf(block).stream()), Stream.of("iron_core")).toList();

    /** The ground blocks' variants alone, which the mark tests read. */
    static final List<String> GROUND_SPRITES = Stream.of("anorthosite", "regolith")
            .flatMap(block -> variantsOf(block).stream()).toList();

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
        return opaqueColorsOf(gooBlock(sprite));
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
        BufferedImage image = gooBlock(sprite);
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
        Set<Integer> foreign = new TreeSet<>();
        for (String sprite : variantsOf("regolith")) {
            foreign.addAll(lunarSpriteColors(sprite));
        }
        for (String sprite : variantsOf("anorthosite")) {
            foreign.removeAll(lunarSpriteColors(sprite));
        }
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
