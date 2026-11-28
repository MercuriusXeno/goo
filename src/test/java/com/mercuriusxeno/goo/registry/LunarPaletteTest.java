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
    static final List<PaletteColor> LUNAR_PALETTE = Stream.of(
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
                    new PaletteColor(0xb0ff7a, "volatile_crystal pulse"),
                    new PaletteColor(0x0a3a3d, "resonator teal"),
                    new PaletteColor(0x0d6f73, "resonator teal"),
                    new PaletteColor(0x1aa6ad, "resonator teal"),
                    new PaletteColor(0x29dfeb, "resonator lit teal"),
                    new PaletteColor(0x7ff6ff, "resonator lit teal"),
                    new PaletteColor(0xd8fdff, "resonator lit teal")),
            alienMaterialColors()).flatMap(colors -> colors).toList();

    /**
     * The black material families' colors, each family's picked stone, glyph, tile, pillar and marker looks shading
     * in its own tones.
     * decision black-runic-material-families-grow-from-the-marker-drafts
     * decision lunar-repaints-are-picked-from-drafts-on-a-page
     */
    private static Stream<PaletteColor> alienMaterialColors() {
        return Stream.of(
                drawnBy("alien framed panels",
                        0x0a0b0d, 0x0f1013, 0x131418, 0x16181c, 0x1d2026, 0x262a31, 0x2b3038, 0x30353e,
                        0x363c46, 0x4a3f9a, 0x5a4cb8),
                drawnBy("alien glaze",
                        0x07070a, 0x0a090e, 0x0a090f, 0x0d0b12, 0x100e17, 0x110f18, 0x13111c, 0x14121d,
                        0x15141e, 0x191624, 0x1d1a2a, 0x201c2e, 0x242034, 0x262136, 0x28233a, 0x302a45,
                        0x332d49, 0x5a4cb8),
                drawnBy("alien iridescent alloy",
                        0x080f10, 0x090d12, 0x0b0d15, 0x0b1117, 0x0b1314, 0x0c0e17, 0x0c1219, 0x0c1516,
                        0x0d1118, 0x0e0d18, 0x0e131a, 0x0f0e1a, 0x100e1b, 0x101320, 0x101d1e, 0x11171f,
                        0x111922, 0x120f1d, 0x12111f, 0x141a24, 0x151424, 0x181428, 0x1b2137, 0x1b3134,
                        0x1d2b3b, 0x1f253d, 0x20263f, 0x20393c, 0x213244, 0x222843, 0x222c3e, 0x223d40,
                        0x233548, 0x24223e, 0x273348, 0x28484c, 0x292646, 0x2a2345, 0x2a2748, 0x2a364d,
                        0x2d2a4d, 0x302950, 0x332b55, 0x4a3f9a, 0x5a4cb8),
                drawnBy("alien polished glass",
                        0x090a0b, 0x0c0c0e, 0x0d0e10, 0x0f0f11, 0x121314, 0x141517, 0x151618, 0x17181b,
                        0x1b1d20, 0x232429, 0x232528, 0x292b2e, 0x2c2d32, 0x2e3034, 0x35383c, 0x3c3f45,
                        0x45484e),
                drawnBy("alien xeno runes",
                        0x09080d, 0x0d0c12, 0x100f16, 0x13111a, 0x1a1723, 0x221e2e, 0x272234, 0x2b263a,
                        0x342e46),
                drawnBy("alien xeno seam",
                        0x09090c, 0x0c0d10, 0x0f1013, 0x111216, 0x16171b, 0x1d1e23, 0x212228, 0x25262c,
                        0x2d2e35, 0x4f43a6, 0xaa9eff),
                drawnBy("alien smooth",
                        0x06080c, 0x0a0d13, 0x0d1017, 0x0f131b, 0x151a24, 0x1c222e, 0x202734, 0x242c3a,
                        0x2c3546, 0x3a3080),
                drawnBy("alien chiseled",
                        0x060908, 0x0a0f0d, 0x0d1310, 0x0f1613, 0x151e1a, 0x1c2722, 0x202c27, 0x24312b,
                        0x2c3b34, 0x3a3080),
                drawnBy("alien inlaid",
                        0x07070d, 0x0b0b13, 0x0e0e17, 0x10101b, 0x161624, 0x1d1d2e, 0x212134, 0x252539,
                        0x2e2e45),
                drawnBy("alien hex cells",
                        0x050a0b, 0x081012, 0x0a1316, 0x0c1619, 0x101c1f, 0x111e21, 0x152428, 0x17272b,
                        0x1b2c31, 0x1e3136, 0x253b41))
                .flatMap(colors -> colors);
    }

    private static Stream<PaletteColor> drawnBy(String drawer, int... rgbs) {
        return IntStream.of(rgbs).mapToObj(rgb -> new PaletteColor(rgb, drawer));
    }

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
    static final List<String> WARREN_SPRITES = Stream.of(
            Stream.of("monolith_marker", "monolith_marker_polished_glass", "monolith_marker_xeno_seam"),
            alienStructureSprites(),
            Stream.of("resonator_ring", "resonator_ring_active", "resonator_tip", "resonator_tip_active",
                    "wireless_emitter", "wireless_emitter_active"))
            .flatMap(sprites -> sprites).toList();

    /**
     * The alien structure family's sprites: each material's stone, glyph and tile, xeno runes' stone and glyph in the
     * two variants its blockstate picks among, and each material's fluted, banded and runed pillar sides and its top.
     * decision warrens-hold-xeno-rich-alien-blocks
     * decision black-runic-material-families-grow-from-the-marker-drafts
     */
    private static Stream<String> alienStructureSprites() {
        List<String> materials = List.of("glazed", "iridescent", "polished_glass", "xeno_runes", "xeno_seam",
                "framed_panels", "smooth", "chiseled", "inlaid", "hex_cells");
        Stream<String> cut = materials.stream().flatMap(material -> material.equals("xeno_runes")
                ? Stream.of("alien_stone_xeno_runes_angular", "alien_stone_xeno_runes_diagonal",
                        "alien_glyph_xeno_runes_gate", "alien_glyph_xeno_runes_fork", "alien_tile_xeno_runes")
                : Stream.of("alien_stone_" + material, "alien_glyph_" + material, "alien_tile_" + material));
        Stream<String> pillars = materials.stream().flatMap(material -> Stream.of("fluted", "banded", "runed", "top")
                .map(part -> "alien_pillar_" + material + "_" + part));
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
