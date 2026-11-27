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
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.registry.SpritePixels.gooBlock;
import static com.mercuriusxeno.goo.registry.SpritePixels.storedArgb;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static final String ROCK_AND_METAL_DRAWERS = "anorthosite, regolith, breccia, meteoric_iron, iron_core "
            + "and native_iron";

    /** The lunar rock and metal blocks, which draw from the silver-grey ramp alone. */
    static final List<String> ROCK_AND_METAL_BLOCKS = List.of("anorthosite", "regolith", "breccia", "meteoric_iron",
            "iron_core", "native_iron");

    /**
     * The silver-grey ramp, sixteen cool steel tones dark to light, first drawn by native iron's drafts.
     * decision lunar-rock-and-metal-blocks-draw-from-the-silver-grey-ramp
     */
    static final List<Integer> SILVER_RAMP = List.of(0x1d1e21, 0x282a2e, 0x333539, 0x3f4146, 0x47494e, 0x52555a,
            0x5d6065, 0x696c71, 0x717479, 0x7d8085, 0x8c8f94, 0x9ea2a8,
            0xb0b4ba, 0xc0c4c9, 0xd0d3d7, 0xe2e5e8);

    /**
     * The lunar palette: the silver-grey ramp, which every rock and metal block draws from, beside crater ice's blues and
     * impact glass's near-blacks, which keep the colors they have, and astral crystal's night blues and twinkling golds.
     * A sprite joining the palette draws from these, or adds a color beside them.
     * decision lunar-rock-and-metal-blocks-draw-from-the-silver-grey-ramp
     * decision lunar-textures-get-form-and-texture-before-color
     */
    static final List<PaletteColor> LUNAR_PALETTE = Stream.concat(
            SILVER_RAMP.stream().map(rgb -> new PaletteColor(rgb, ROCK_AND_METAL_DRAWERS)),
            Stream.of(
                    new PaletteColor(0xeef3f6, "crater_ice"),
                    new PaletteColor(0xdfe8ee, "crater_ice"),
                    new PaletteColor(0xcfdce5, "crater_ice"),
                    new PaletteColor(0xbecfdb, "crater_ice"),
                    new PaletteColor(0xadc1d0, "crater_ice"),
                    new PaletteColor(0x9cb3c4, "crater_ice"),
                    new PaletteColor(0x1d1e21, "impact_glass"),
                    new PaletteColor(0x121315, "impact_glass"),
                    new PaletteColor(0x141c52, "astral_crystal"),
                    new PaletteColor(0x1e2c80, "astral_crystal"),
                    new PaletteColor(0x2a3c9a, "astral_crystal"),
                    new PaletteColor(0x3a50b4, "astral_crystal"),
                    new PaletteColor(0x5470cc, "astral_crystal"),
                    new PaletteColor(0x7a94de, "astral_crystal"),
                    new PaletteColor(0xa9bcef, "astral_crystal"),
                    new PaletteColor(0xb8a860, "astral_crystal twinkle"),
                    new PaletteColor(0xf0e090, "astral_crystal twinkle"),
                    new PaletteColor(0xfff4b0, "astral_crystal twinkle"),
                    new PaletteColor(0x2e2766, "monolith_marker rune shade"),
                    new PaletteColor(0x6a5acd, "monolith_marker rune"),
                    new PaletteColor(0x7969dc, "monolith_marker rune"),
                    new PaletteColor(0x8878eb, "monolith_marker rune"),
                    new PaletteColor(0x9282f5, "monolith_marker rune"),
                    new PaletteColor(0x0e0d14, "alien glaze"),
                    new PaletteColor(0x15131f, "alien glaze"),
                    new PaletteColor(0x1d1a2b, "alien glaze"),
                    new PaletteColor(0x272338, "alien glaze"),
                    new PaletteColor(0x353047, "alien glaze"),
                    new PaletteColor(0x15192a, "alien iridescent alloy"),
                    new PaletteColor(0x1c1a30, "alien iridescent alloy"),
                    new PaletteColor(0x201b35, "alien iridescent alloy"),
                    new PaletteColor(0x1a2230, "alien iridescent alloy"),
                    new PaletteColor(0x152628, "alien iridescent alloy"),
                    new PaletteColor(0x16212d, "alien iridescent alloy"),
                    new PaletteColor(0x16171c, "alien facets"),
                    new PaletteColor(0x1b1c22, "alien facets"),
                    new PaletteColor(0x202229, "alien facets"),
                    new PaletteColor(0x262830, "alien facets"),
                    new PaletteColor(0x33353d, "alien facet edge"),
                    new PaletteColor(0x101115, "alien facet edge"),
                    new PaletteColor(0x3a3d45, "alien joint and cut edge"),
                    new PaletteColor(0x0b0c0f, "alien joint and cut"),
                    new PaletteColor(0x0c3a0a, "volatile_crystal"),
                    new PaletteColor(0x1d7a14, "volatile_crystal"),
                    new PaletteColor(0x30b81e, "volatile_crystal"),
                    new PaletteColor(0x6cf03a, "volatile_crystal"),
                    new PaletteColor(0xd8ffb0, "volatile_crystal"),
                    new PaletteColor(0x269919, "volatile_crystal pulse"),
                    new PaletteColor(0x34db19, "volatile_crystal pulse"),
                    new PaletteColor(0x39ff14, "volatile_crystal pulse"),
                    new PaletteColor(0x60ff2c, "volatile_crystal pulse"),
                    new PaletteColor(0x88ff44, "volatile_crystal pulse"),
                    new PaletteColor(0xb0ff7a, "volatile_crystal pulse"))).toList();

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

    /**
     * Astral crystal's growths and volatile crystal, each an animated strip of its frames, drawn as cutouts over clear
     * space.
     * decision astral-crystal-is-lunars-scarce-rich-block
     * decision volatile-crystals-are-rich-in-crystal-and-unstable
     */
    static final List<String> CUTOUT_SPRITES = Stream.concat(variantsOf("astral_crystal").stream(),
            Stream.of("volatile_crystal")).toList();

    /**
     * The warrens' alien blocks, black stone from the palette's near-blacks carrying runes in xeno's own violets.
     * decision warrens-hold-xeno-rich-alien-blocks
     */
    static final List<String> WARREN_SPRITES = Stream.concat(Stream.of("monolith_marker"), alienStructureSprites())
            .toList();

    /**
     * The alien structure family's sprites: stone, tile and every glyph in each refined material, and each pillar's
     * side and end.
     * decision warrens-hold-xeno-rich-alien-blocks
     */
    private static Stream<String> alienStructureSprites() {
        List<String> materials = List.of("glazed", "iridescent", "faceted");
        Stream<String> cut = materials.stream().flatMap(material -> Stream.concat(
                Stream.of("alien_stone_" + material, "alien_tile_" + material),
                Stream.of("eye", "maze", "ring").map(mark -> "alien_glyph_" + material + "_" + mark)));
        Stream<String> pillars = Stream.of("fluted", "banded", "runed")
                .flatMap(style -> Stream.of("alien_pillar_" + style, "alien_pillar_" + style + "_top"));
        return Stream.concat(cut, pillars);
    }

    /** The sprites under assets/goo/textures/block/ held to the palette: every lunar block's variants. */
    static final List<String> LUNAR_SPRITES = Stream.of(GROUND_SPRITES.stream(), Stream.of("iron_core"),
            LANDFORM_SPRITES.stream(), CUTOUT_SPRITES.stream(), WARREN_SPRITES.stream())
            .flatMap(sprites -> sprites).toList();

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
        boolean cutout = CUTOUT_SPRITES.contains(sprite);
        Set<Integer> offPalette = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                if (cutout && argb >>> 24 == 0) {
                    continue;
                }
                boolean opaqueAsDrawn = translucent ? argb >>> 24 != 0 : argb >>> 24 == 0xff;
                if (!opaqueAsDrawn || !palette.contains(argb & 0xffffff)) {
                    offPalette.add(argb);
                }
            }
        }
        assertTrue(offPalette.isEmpty(), sprite + " draws off the palette: "
                + offPalette.stream().map(argb -> String.format("#%08x", argb)).collect(Collectors.joining(" ")));
    }

    /** The rock and metal blocks' sprites, every variant their blockstates pick among. */
    static Stream<String> rockAndMetalSprites() {
        return LUNAR_SPRITES.stream().filter(sprite -> ROCK_AND_METAL_BLOCKS.stream()
                .anyMatch(block -> sprite.equals(block) || sprite.startsWith(block + "_")));
    }

    /**
     * A rock or metal sprite draws the silver-grey ramp alone, so no rock picks up an ice blue the wider palette
     * allows.
     * decision lunar-rock-and-metal-blocks-draw-from-the-silver-grey-ramp
     */
    @ParameterizedTest
    @MethodSource("rockAndMetalSprites")
    void everyPixelOfARockOrMetalSpriteIsARampTone(String sprite) throws IOException {
        BufferedImage image = gooBlock(sprite);
        Set<Integer> offRamp = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                if (argb >>> 24 != 0xff || !SILVER_RAMP.contains(argb & 0xffffff)) {
                    offRamp.add(argb);
                }
            }
        }
        assertTrue(offRamp.isEmpty(), sprite + " draws off the silver-grey ramp: "
                + offRamp.stream().map(argb -> String.format("#%08x", argb)).collect(Collectors.joining(" ")));
    }

    /**
     * Every palette color a rock or metal block draws is a ramp tone, and every ramp tone is one of them.
     * decision lunar-rock-and-metal-blocks-draw-from-the-silver-grey-ramp
     */
    @Test
    void theRockAndMetalBlocksPaletteColorsAreTheRamp() {
        Set<Integer> drawnByRock = LUNAR_PALETTE.stream()
                .filter(color -> ROCK_AND_METAL_BLOCKS.stream().anyMatch(color.drawnBy()::contains))
                .map(PaletteColor::rgb).collect(Collectors.toCollection(TreeSet::new));
        assertEquals(new TreeSet<>(SILVER_RAMP), drawnByRock);
    }
}
