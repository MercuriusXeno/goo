package com.mercuriusxeno.goo.ability;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vacuum's icon is drawn for it in Void's palette at the size of Void's other
 * icons, never another icon copied or mirrored as a stand-in
 * (decision vacuum-icon-is-a-small-vacuum).
 */
class VoidVacuumIconTest {

    private static final String ICONS = "/assets/goo/textures/goo/ability/";
    private static final String VACUUM = "void_vacuum.png";
    /** Void's palette, read off void_erase.png, void_hole.png and void_outsider.png. */
    private static final Set<Integer> VOID_PALETTE = Set.of(0x000000, 0x0E0818, 0x1A0E28, 0x22123A, 0x2A1846,
            0x4A2C78, 0x7A52B0, 0xB48CE8);
    private static final int RGB = 0xFFFFFF;
    private static final int OPAQUE_SHIFT = 24;

    @Test
    void vacuumsIconStandsAtTheSizeOfErasesIcon() throws IOException {
        BufferedImage vacuum = icon(VACUUM);
        BufferedImage erase = icon("void_erase.png");

        assertEquals(erase.getWidth(), vacuum.getWidth());
        assertEquals(erase.getHeight(), vacuum.getHeight());
    }

    @Test
    void vacuumsIconIsDrawnInVoidsPalette() throws IOException {
        BufferedImage vacuum = icon(VACUUM);
        for (int y = 0; y < vacuum.getHeight(); y++) {
            for (int x = 0; x < vacuum.getWidth(); x++) {
                int argb = vacuum.getRGB(x, y);
                if (argb >>> OPAQUE_SHIFT != 0) {
                    assertTrue(VOID_PALETTE.contains(argb & RGB),
                            String.format("pixel %d,%d is #%06X, outside Void's palette", x, y, argb & RGB));
                }
            }
        }
    }

    @Test
    void vacuumsIconIsNoOtherIconCopiedOrMirrored() throws IOException, URISyntaxException {
        BufferedImage vacuum = icon(VACUUM);
        for (String other : otherIcons()) {
            BufferedImage icon = icon(other);
            assertFalse(samePixels(vacuum, icon, false), VACUUM + " copies " + other);
            assertFalse(samePixels(vacuum, icon, true), VACUUM + " mirrors " + other);
        }
    }

    private static boolean samePixels(BufferedImage one, BufferedImage other, boolean mirrored) {
        if (one.getWidth() != other.getWidth() || one.getHeight() != other.getHeight()) {
            return false;
        }
        for (int y = 0; y < one.getHeight(); y++) {
            for (int x = 0; x < one.getWidth(); x++) {
                int otherX = mirrored ? other.getWidth() - 1 - x : x;
                if (one.getRGB(x, y) != other.getRGB(otherX, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> otherIcons() throws IOException, URISyntaxException {
        URL folder = VoidVacuumIconTest.class.getResource(ICONS);
        assertNotNull(folder, ICONS + " is not on the classpath");
        try (Stream<Path> icons = Files.list(Path.of(folder.toURI()))) {
            return icons.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".png") && !name.equals(VACUUM)).toList();
        }
    }

    private static BufferedImage icon(String name) throws IOException {
        try (InputStream in = VoidVacuumIconTest.class.getResourceAsStream(ICONS + name)) {
            assertNotNull(in, name + " is not on the classpath");
            return ImageIO.read(in);
        }
    }
}
