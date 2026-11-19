package com.mercuriusxeno.goo.client.radial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the residual ability icons, each world goo's icon read through the
 * classpath as the 16x16 texels it ships: a gray block under every icon,
 * which any goo may drip over so long as it reads as a block, and each
 * icon's own goo and mark drawn on it.
 */
class ResidualAbilityIconsTest {

    // residual-icons-draw-one-gray-block-with-the-types-goo-on-top
    // residual-icon-style-reaches-all-seven-and-later-world-goos
    // updraft-icon-is-a-floating-slime-with-up-arrows
    // operator ruling 2026-11-17: Nectar takes the residual block icon, mark A, a heart, over its own goo
    // every-residual-goo-ability-wears-the-residual-badge-and-takes-the-residual-icon
    // each-residual-icon-draws-its-goo-in-its-own-shape
    // residual-icon-marks-are-picked-from-drafts-in-an-artifact: Spores takes draft A, Spill, operator pick 2026-11-18
    // operator pick 2026-11-18: Nitro takes draft B1, the blast over a lumpy mound of unstable goo
    // operator ruling 2026-11-18: Mortar takes draft F, its goo stacked in three held steps with cracks through it
    // operator pick 2026-11-18: Stitch takes draft C, a sheep's face over a scalloped wool cloud of yarn goo
    // operator pick 2026-11-18: Dowsing takes draft E, a skinny amethyst sliver floating over hanging astral goo
    // dowsing-icon-is-a-dowsing-rod
    /** The icons drawing the residual style; a later residual joins by its icon's name. */
    static final List<String> RESIDUAL_ICONS = List.of("weird_goop", "kinetic_slick", "blaze_molten", "deep_host",
            "crystal_shatter", "hex_leech", "typhoon_updraft", "jelly_nectar", "pulse_signal",
            "metal_spikes", "zombie_infest", "leaf_vines", "unstable_nitro", "quantum_phase", "shroom_spores",
            "rock_mortar", "yarn_stitch", "astral_dowsing");

    private static final String SHATTER = "crystal_shatter";
    private static final String NITRO = "unstable_nitro";
    private static final String STITCH = "yarn_stitch";
    private static final String DOWSING = "astral_dowsing";
    private static final String GOO_TYPE = "/data/goo/goo/goo_type/%s.json";
    private static final int HEX = 16;
    /** How far a goo texel's hue may lie from its type's wheel hue, as a share of the hue circle: 40 degrees. */
    private static final float HUE_BAND = 40f / 360f;
    /** The saturation under which a color reads as gray rather than as a hue. */
    private static final float GRAY_SATURATION = 0.2f;
    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final int SIDE = 16;
    private static final int ALPHA_SHIFT = 24;
    private static final int BLOCK_LEFT = 2;
    private static final int BLOCK_RIGHT = 13;
    private static final int BLOCK_BODY_TOP = 11;
    private static final int BLOCK_BOTTOM = 14;
    private static final int BLOCK_SIDES_TOP = 10;

    /**
     * The block's mask: its body rows and its side edges from just under the
     * goo down, left out when two icons' goo shapes are compared.
     */
    static boolean inSharedBlock(int x, int y) {
        boolean inColumns = x >= BLOCK_LEFT && x <= BLOCK_RIGHT;
        boolean inBody = y >= BLOCK_BODY_TOP && y <= BLOCK_BOTTOM;
        boolean onSide = (x == BLOCK_LEFT || x == BLOCK_RIGHT) && y >= BLOCK_SIDES_TOP && y <= BLOCK_BOTTOM;
        return inColumns && inBody || onSide;
    }

    @ParameterizedTest
    @FieldSource("RESIDUAL_ICONS")
    void residualIconIsSixteenBySixteen(String ability) throws IOException {
        BufferedImage icon = read(ability);
        assertEquals(SIDE, icon.getWidth(), ability + " width");
        assertEquals(SIDE, icon.getHeight(), ability + " height");
    }

    // every-icon-its-own-drawing
    @Test
    void noResidualIconCopiesAnotherOutsideTheBlock() throws IOException {
        List<String> copies = new ArrayList<>();
        for (int first = 0; first < RESIDUAL_ICONS.size(); first++) {
            for (int second = first + 1; second < RESIDUAL_ICONS.size(); second++) {
                String one = RESIDUAL_ICONS.get(first);
                String other = RESIDUAL_ICONS.get(second);
                String drawing = drawingOutsideBlock(read(one), false);
                if (drawing.equals(drawingOutsideBlock(read(other), false))) {
                    copies.add(one + " draws " + other + "'s shape");
                }
                if (drawing.equals(drawingOutsideBlock(read(other), true))) {
                    copies.add(one + " draws " + other + "'s shape mirrored");
                }
            }
        }
        assertTrue(copies.isEmpty(), String.join("; ", copies));
    }

    /**
     * Nitro's goo, its texels in unstable's goo colors outside the shared
     * block, takes a shape no other residual icon's goo takes, drawn or
     * mirrored, whatever mark sits over it.
     * each-residual-icon-draws-its-goo-in-its-own-shape
     */
    @Test
    void nitrosGooTakesAShapeNoOtherResidualIconsGooTakes() throws IOException {
        assertGooTakesItsOwnShape(NITRO, "unstable");
    }

    /**
     * Dowsing's goo, its texels in astral's goo colors outside the shared
     * block, takes a shape no other residual icon's goo takes, drawn or
     * mirrored, whatever mark sits over it.
     * each-residual-icon-draws-its-goo-in-its-own-shape
     * dowsing-icon-is-a-dowsing-rod
     */
    @Test
    void dowsingsGooTakesAShapeNoOtherResidualIconsGooTakes() throws IOException {
        assertGooTakesItsOwnShape(DOWSING, "astral");
    }

    private static void assertGooTakesItsOwnShape(String ability, String type) throws IOException {
        String goo = gooMask(ability, false);
        List<String> copies = new ArrayList<>();
        for (String other : RESIDUAL_ICONS) {
            if (!other.equals(ability) && goo.equals(gooMask(other, false))) {
                copies.add(ability + " draws " + other + "'s goo");
            }
            if (!other.equals(ability) && goo.equals(gooMask(other, true))) {
                copies.add(ability + " draws " + other + "'s goo mirrored");
            }
        }
        assertTrue(goo.contains("#"), ability + " draws no goo in " + type + "'s colors");
        assertTrue(copies.isEmpty(), String.join("; ", copies));
    }

    /**
     * Stitch's goo, its texels in yarn's goo colors outside the shared block,
     * takes a shape no other residual icon's goo takes, drawn or mirrored,
     * whatever mark sits over it.
     * each-residual-icon-draws-its-goo-in-its-own-shape
     */
    @Test
    void stitchsGooTakesAShapeNoOtherResidualIconsGooTakes() throws IOException {
        String stitch = gooMask(STITCH, false);
        List<String> copies = new ArrayList<>();
        for (String other : RESIDUAL_ICONS) {
            if (!other.equals(STITCH) && stitch.equals(gooMask(other, false))) {
                copies.add(STITCH + " draws " + other + "'s goo");
            }
            if (!other.equals(STITCH) && stitch.equals(gooMask(other, true))) {
                copies.add(STITCH + " draws " + other + "'s goo mirrored");
            }
        }
        assertTrue(stitch.contains("#"), STITCH + " draws no goo in yarn's colors");
        assertTrue(copies.isEmpty(), String.join("; ", copies));
    }

    /**
     * An icon's goo texels, '#' for each, read left to right or mirrored:
     * opaque, outside the shared block, and colored in its goo type's hue,
     * within a band of the type's wheel color, where a mark's texels lie
     * outside it. A type whose wheel holds next to no color takes its goo
     * as the texels holding next to none.
     */
    static String gooMask(String ability, boolean mirrored) throws IOException {
        BufferedImage icon = read(ability);
        float[] wheel = hsb(wheelOf(ability.substring(0, ability.indexOf('_'))));
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < SIDE; y++) {
            for (int column = 0; column < SIDE; column++) {
                int argb = icon.getRGB(mirrored ? SIDE - 1 - column : column, y);
                boolean goo = !inSharedBlock(column, y) && isOpaque(argb) && inGooColors(hsb(argb), wheel);
                rows.append(goo ? '#' : '.');
            }
            rows.append('\n');
        }
        return rows.toString();
    }

    private static boolean inGooColors(float[] texel, float[] wheel) {
        if (wheel[1] < GRAY_SATURATION) {
            return texel[1] < GRAY_SATURATION;
        }
        float apart = Math.abs(texel[0] - wheel[0]);
        return texel[1] >= GRAY_SATURATION && Math.min(apart, 1f - apart) <= HUE_BAND;
    }

    private static float[] hsb(int rgb) {
        return Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
    }

    /** The goo type's wheel color, as its definition names it. */
    private static int wheelOf(String type) throws IOException {
        String path = GOO_TYPE.formatted(type);
        try (InputStream in = ResidualAbilityIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            JsonObject definition = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return Integer.parseInt(definition.get("wheel").getAsString(), HEX);
        }
    }

    // shatter-icon-is-a-block-with-crystal-goo-and-shards-over-it
    @Test
    void shattersShapeIsNoOtherAbilityIconsCopiedOrMirrored() throws IOException, URISyntaxException {
        String shatter = shape(read(SHATTER), false, false);
        List<String> copies = new ArrayList<>();
        for (String other : otherAbilityIcons(SHATTER)) {
            BufferedImage icon = read(other);
            if (shatter.equals(shape(icon, false, false))) {
                copies.add(SHATTER + " draws " + other + "'s shape");
            }
            if (shatter.equals(shape(icon, true, false))) {
                copies.add(SHATTER + " draws " + other + "'s shape mirrored");
            }
        }
        assertTrue(copies.isEmpty(), String.join("; ", copies));
    }

    /**
     * The icon's opaque texels, '#' for opaque, read left to right or
     * mirrored, the shared block's mask left clear where asked.
     */
    static String shape(BufferedImage icon, boolean mirrored, boolean outsideBlockOnly) {
        StringBuilder rows = new StringBuilder();
        for (int y = 0; y < icon.getHeight(); y++) {
            for (int column = 0; column < icon.getWidth(); column++) {
                int x = mirrored ? icon.getWidth() - 1 - column : column;
                boolean masked = outsideBlockOnly && inSharedBlock(column, y);
                rows.append(!masked && isOpaque(icon.getRGB(x, y)) ? '#' : '.');
            }
            rows.append('\n');
        }
        return rows.toString();
    }

    /** Every shipped ability icon's name but the one given. */
    static List<String> otherAbilityIcons(String ability) throws IOException, URISyntaxException {
        URL folder = ResidualAbilityIconsTest.class.getResource(ABILITY_ICONS);
        assertNotNull(folder, ABILITY_ICONS + " is not on the classpath");
        try (Stream<Path> icons = Files.list(Path.of(folder.toURI()))) {
            return icons.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".png"))
                    .map(name -> name.substring(0, name.length() - ".png".length()))
                    .filter(name -> !name.equals(ability)).toList();
        }
    }

    /**
     * The icon's opaque texels outside the shared block, '#' for opaque, read
     * left to right or mirrored; a recolor draws the same shape.
     */
    static String drawingOutsideBlock(BufferedImage icon, boolean mirrored) {
        return shape(icon, mirrored, true);
    }

    private static boolean isOpaque(int argb) {
        return (argb >>> ALPHA_SHIFT) > 0;
    }

    static BufferedImage read(String ability) throws IOException {
        String path = ABILITY_ICONS + ability + ".png";
        try (InputStream in = ResidualAbilityIconsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            return ImageIO.read(in);
        }
    }
}
