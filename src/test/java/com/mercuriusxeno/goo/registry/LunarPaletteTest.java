package com.mercuriusxeno.goo.registry;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.registry.SpritePixels.gooBlock;
import static com.mercuriusxeno.goo.registry.SpritePixels.storedArgb;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The lunar palette: the colors the operator picked from drafts, which every lunar sprite draws from alone, so the
 * dimension's colors stay consistent.
 * decision lunar-blocks-are-repainted-from-one-shared-palette
 * decision lunar-textures-are-repainted-by-hand-from-vanilla-palettes
 */
class LunarPaletteTest {

    /** One palette color and the block whose sprite draws it. */
    record PaletteColor(int rgb, String drawnBy) {
        @Override
        public String toString() {
            return String.format("#%06x for %s", rgb, drawnBy);
        }
    }

    /**
     * Anorthosite's pale feldspar grain and its mid brown-grey specks, after the operator's sample rock, with off-white
     * glints the ground blocks share; regolith, the dust of the pale parts; the iron core's form B steel. A landform
     * sprite joining the palette draws from these, or adds a color beside them.
     * decision lunar-textures-get-form-and-texture-before-color
     */
    static final List<PaletteColor> LUNAR_PALETTE = List.of(
            new PaletteColor(0xdcd3cd, "anorthosite and regolith glints"),
            new PaletteColor(0xc8bdb6, "anorthosite"),
            new PaletteColor(0xb4a8a1, "anorthosite"),
            new PaletteColor(0x9f938d, "anorthosite"),
            new PaletteColor(0x8c817c, "anorthosite"),
            new PaletteColor(0x6e6460, "anorthosite"),
            new PaletteColor(0x564e4b, "anorthosite"),
            new PaletteColor(0xcfc5be, "regolith"),
            new PaletteColor(0xc2b7b0, "regolith"),
            new PaletteColor(0xa69a93, "regolith"),
            new PaletteColor(0xb0b4ba, "iron_core"),
            new PaletteColor(0x8c8f94, "iron_core"),
            new PaletteColor(0x717479, "iron_core"),
            new PaletteColor(0x52555a, "iron_core"),
            new PaletteColor(0x3f4146, "iron_core"),
            new PaletteColor(0x333539, "iron_core"),
            new PaletteColor(0x282a2e, "iron_core"),
            new PaletteColor(0xbdb9b4, "breccia matrix"),
            new PaletteColor(0xb1ada8, "breccia matrix"),
            new PaletteColor(0xa5a19c, "breccia matrix"),
            new PaletteColor(0x99958f, "breccia matrix"),
            new PaletteColor(0x8d8984, "breccia matrix"),
            new PaletteColor(0x7f7b76, "breccia matrix"),
            new PaletteColor(0x716d68, "breccia matrix"),
            new PaletteColor(0xeef3f6, "crater_ice"),
            new PaletteColor(0xdfe8ee, "crater_ice"),
            new PaletteColor(0xcfdce5, "crater_ice"),
            new PaletteColor(0xbecfdb, "crater_ice"),
            new PaletteColor(0xadc1d0, "crater_ice"),
            new PaletteColor(0x9cb3c4, "crater_ice"),
            new PaletteColor(0xc9ccd1, "meteoric_iron glint"),
            new PaletteColor(0x1d1e21, "impact_glass and native_iron"),
            new PaletteColor(0x121315, "impact_glass"),
            new PaletteColor(0x47494e, "native_iron"),
            new PaletteColor(0x5d6065, "native_iron"),
            new PaletteColor(0x696c71, "native_iron"),
            new PaletteColor(0x7d8085, "native_iron"),
            new PaletteColor(0x9ea2a8, "native_iron"),
            new PaletteColor(0xc0c4c9, "native_iron"),
            new PaletteColor(0xd0d3d7, "native_iron"),
            new PaletteColor(0xe2e5e8, "native_iron"));

    /**
     * A ground block's sprites: its own and the variants its blockstate picks among per block, sharing one border band.
     * decision lunar-repaints-are-picked-from-drafts-on-a-page
     */
    static List<String> variantsOf(String block) {
        return variantsOf(block, 4);
    }

    /** A block's sprites when its blockstate picks among some count of variants: its own, then _2 onward. */
    static List<String> variantsOf(String block, int count) {
        return Stream.concat(Stream.of(block), IntStream.rangeClosed(2, count).mapToObj(k -> block + "_" + k))
                .toList();
    }

    /** The ground blocks' variants, which the mark tests read. */
    static final List<String> GROUND_SPRITES = Stream.of("anorthosite", "regolith")
            .flatMap(block -> variantsOf(block).stream()).toList();

    /**
     * The landform blocks' variants, each picked from drafts beside the ground blocks.
     * decision lunar-repaints-are-picked-from-drafts-on-a-page
     */
    static final List<String> LANDFORM_SPRITES = Stream.concat(Stream.of("meteoric_iron", "impact_glass", "breccia",
            "crater_ice").flatMap(block -> variantsOf(block).stream()), variantsOf("native_iron", 6).stream()).toList();

    /** The sprites under assets/goo/textures/block/ held to the palette: every lunar block's variants. */
    static final List<String> LUNAR_SPRITES = Stream.of(GROUND_SPRITES.stream(), Stream.of("iron_core"),
            LANDFORM_SPRITES.stream()).flatMap(sprites -> sprites).toList();

    /** Impact glass and crater ice draw translucent, so their pixels are held to the palette by color alone. */
    static final List<String> TRANSLUCENT_SPRITES = Stream.of("impact_glass", "crater_ice")
            .flatMap(block -> variantsOf(block).stream()).toList();

    static Stream<String> lunarSprites() {
        return LUNAR_SPRITES.stream();
    }

    @ParameterizedTest
    @MethodSource("lunarSprites")
    void everyPixelOfTheSpriteIsAPaletteColor(String sprite) throws IOException {
        BufferedImage image = gooBlock(sprite);
        Set<Integer> palette = LUNAR_PALETTE.stream().map(PaletteColor::rgb).collect(Collectors.toSet());
        boolean translucent = TRANSLUCENT_SPRITES.contains(sprite);
        Set<Integer> offPalette = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                boolean opaqueAsDrawn = translucent ? argb >>> 24 != 0 : argb >>> 24 == 0xff;
                if (!opaqueAsDrawn || !palette.contains(argb & 0xffffff)) {
                    offPalette.add(argb);
                }
            }
        }
        assertTrue(offPalette.isEmpty(), sprite + " draws off the palette: "
                + offPalette.stream().map(argb -> String.format("#%08x", argb)).collect(Collectors.joining(" ")));
    }
}
