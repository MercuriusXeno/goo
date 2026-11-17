package com.mercuriusxeno.goo.client.radial;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
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
 * classpath as the 16x16 texels it ships: one gray block shared texel for
 * texel under every icon, and each icon's own goo and mark drawn over it.
 */
class ResidualAbilityIconsTest {

    // residual-icons-draw-one-gray-block-with-the-types-goo-on-top
    // residual-icon-style-reaches-all-seven-and-later-world-goos
    // updraft-icon-is-a-floating-slime-with-up-arrows
    /** The icons drawing the residual style; a later residual joins by its icon's name. */
    static final List<String> RESIDUAL_ICONS = List.of("weird_goop", "kinetic_slick", "blaze_molten", "deep_host",
            "crystal_shatter", "hex_leech", "typhoon_updraft");

    private static final String SHATTER = "crystal_shatter";
    private static final String ABILITY_ICONS = "/assets/goo/textures/goo/ability/";
    private static final int SIDE = 16;
    private static final int ALPHA_SHIFT = 24;
    private static final int BLOCK_LEFT = 2;
    private static final int BLOCK_RIGHT = 13;
    private static final int BLOCK_BODY_TOP = 11;
    private static final int BLOCK_BOTTOM = 14;
    private static final int BLOCK_SIDES_TOP = 10;

    /**
     * The shared block's mask: the body rows no goo reaches, and the block's
     * side edges from just under the goo down. Every residual icon holds the
     * same texels here; the goo and the mark draw only outside it.
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

    @Test
    void everyResidualIconHoldsTheSameBlock() throws IOException {
        String reference = RESIDUAL_ICONS.getFirst();
        BufferedImage block = read(reference);
        List<String> differences = new ArrayList<>();
        for (String ability : RESIDUAL_ICONS) {
            BufferedImage icon = read(ability);
            for (int y = 0; y < SIDE; y++) {
                for (int x = 0; x < SIDE; x++) {
                    if (inSharedBlock(x, y) && !isOpaque(icon.getRGB(x, y))) {
                        differences.add(ability + " leaves the block clear at " + x + "," + y);
                    } else if (inSharedBlock(x, y) && icon.getRGB(x, y) != block.getRGB(x, y)) {
                        differences.add(String.format("%s differs from %s at %d,%d", ability, reference, x, y));
                    }
                }
            }
        }
        assertEquals(List.of(), differences, "texels inside the shared block mask");
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
