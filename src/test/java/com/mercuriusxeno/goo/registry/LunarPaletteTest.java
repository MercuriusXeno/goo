package com.mercuriusxeno.goo.registry;

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
     * Anorthosite's pale feldspar grain and its mid brown-grey specks, after the operator's sample rock; regolith, the
     * dust of the pale parts; the iron core's form B steel. A landform sprite joining the palette draws from these, or
     * adds a color beside them.
     * decision lunar-textures-get-form-and-texture-before-color
     */
    static final List<PaletteColor> LUNAR_PALETTE = List.of(
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
            new PaletteColor(0x282a2e, "iron_core"));

    /**
     * A ground block's sprites: its own and the variants its blockstate picks among per block, sharing one border band.
     * decision lunar-repaints-are-picked-from-drafts-on-a-page
     */
    static List<String> variantsOf(String block) {
        return List.of(block, block + "_2", block + "_3", block + "_4");
    }

    /** The ground blocks' variants, which the mark tests read. */
    static final List<String> GROUND_SPRITES = Stream.of("anorthosite", "regolith")
            .flatMap(block -> variantsOf(block).stream()).toList();

    /** The sprites under assets/goo/textures/block/ held to the palette: both ground blocks' variants and the iron core. */
    static final List<String> LUNAR_SPRITES = Stream.concat(GROUND_SPRITES.stream(), Stream.of("iron_core")).toList();

    static Stream<String> lunarSprites() {
        return LUNAR_SPRITES.stream();
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
}
