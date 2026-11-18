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
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Dowsing's ability icon is a dowsing rod of its own: Astral's icon size,
 * drawn only in the colors Astral's other ability icons wear, and no copy,
 * recolor or left-right mirror of any other shipped ability icon, read by
 * the shape of its opaque pixels.
 * dowsing-icon-is-a-dowsing-rod
 */
class DowsingIconTest {

    private static final String ICONS = "assets/goo/textures/goo/ability/";
    private static final String DOWSING = "astral_dowsing.png";
    /** Astral's other ability icons, whose colors are Astral's icon palette. */
    private static final List<String> ASTRAL_ICONS = List.of("astral_scry.png", "astral_meteo.png",
            "astral_lunar.png", "astral_terran.png");

    @Test
    void dowsingStandsAtTheSizeOfAstralsIcons() throws IOException {
        BufferedImage dowsing = icon(DOWSING);
        BufferedImage scry = icon(ASTRAL_ICONS.getFirst());

        assertEquals(List.of(scry.getWidth(), scry.getHeight()), List.of(dowsing.getWidth(), dowsing.getHeight()));
    }

    @Test
    void dowsingIsDrawnInAstralsPalette() throws IOException {
        Set<String> palette = new TreeSet<>();
        for (String astral : ASTRAL_ICONS) {
            palette.addAll(opaqueColorsOf(icon(astral)));
        }
        Set<String> outside = new TreeSet<>(opaqueColorsOf(icon(DOWSING)));
        outside.removeAll(palette);

        assertEquals(Set.of(), outside, "colors outside Astral's icon palette");
    }

    @Test
    void dowsingIsNoCopyRecolorOrMirrorOfAnotherIcon() throws IOException, URISyntaxException {
        String dowsing = maskOf(icon(DOWSING), false);
        Set<String> sharing = new TreeSet<>();
        for (String other : shippedIconsBut(DOWSING)) {
            BufferedImage image = icon(other);
            if (dowsing.equals(maskOf(image, false)) || dowsing.equals(maskOf(image, true))) {
                sharing.add(other);
            }
        }

        assertEquals(Set.of(), sharing, "icons whose shape Dowsing's repeats");
    }

    private static Set<String> opaqueColorsOf(BufferedImage image) {
        Set<String> colors = new TreeSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                if (argb >>> 24 != 0) {
                    colors.add(String.format("%06X", argb & 0xFFFFFF));
                }
            }
        }
        return colors;
    }

    /** The icon's opaque pixels as rows of marks, read right to left where mirrored. */
    private static String maskOf(BufferedImage image, boolean mirrored) {
        StringBuilder mask = new StringBuilder();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int column = mirrored ? image.getWidth() - 1 - x : x;
                mask.append(image.getRGB(column, y) >>> 24 == 0 ? '.' : '#');
            }
            mask.append('\n');
        }
        return mask.toString();
    }

    private static List<String> shippedIconsBut(String skipped) throws IOException, URISyntaxException {
        URL folder = DowsingIconTest.class.getClassLoader().getResource(ICONS);
        assertNotNull(folder, ICONS);
        try (Stream<Path> files = Files.list(Path.of(folder.toURI()))) {
            return files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".png") && !name.equals(skipped)).sorted()
                    .collect(Collectors.toList());
        }
    }

    private static BufferedImage icon(String name) throws IOException {
        try (InputStream in = DowsingIconTest.class.getResourceAsStream("/" + ICONS + name)) {
            assertNotNull(in, ICONS + name);
            return ImageIO.read(in);
        }
    }
}
