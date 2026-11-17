package com.mercuriusxeno.goo.registry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.registry.SpritePixels.gooBlock;
import static com.mercuriusxeno.goo.registry.SpritePixels.storedArgb;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dark marks of anorthosite and regolith: a mark is a texel darker than its sprite's mean luminance by more than
 * two standard deviations, and a sprite tiles, so neighbours wrap across its edges.
 * decision lunar-repaints-are-picked-from-drafts-on-a-page
 * decision lunar-textures-get-form-and-texture-before-color
 */
class LunarTextureMarksTest {

    record Texel(int x, int y) {
        @Override
        public String toString() {
            return "(" + x + "," + y + ")";
        }
    }

    private static double luminance(int argb) {
        return 0.299 * (argb >> 16 & 0xff) + 0.587 * (argb >> 8 & 0xff) + 0.114 * (argb & 0xff);
    }

    static Set<Texel> darkMarksOf(BufferedImage image) {
        List<Texel> texels = new ArrayList<>();
        List<Double> lights = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = storedArgb(image, x, y);
                if (argb >>> 24 != 0) {
                    texels.add(new Texel(x, y));
                    lights.add(luminance(argb));
                }
            }
        }
        double mean = lights.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double deviation = Math.sqrt(lights.stream().mapToDouble(light -> (light - mean) * (light - mean))
                .average().orElse(0));
        Set<Texel> marks = new HashSet<>();
        for (int i = 0; i < texels.size(); i++) {
            if (lights.get(i) < mean - 2 * deviation) {
                marks.add(texels.get(i));
            }
        }
        return marks;
    }

    private static Set<Texel> darkMarksOf(String sprite) throws IOException {
        return darkMarksOf(gooBlock(sprite));
    }

    private static boolean touchOnTheTile(Texel a, Texel b, int width, int height) {
        int dx = Math.floorMod(a.x() - b.x(), width);
        int dy = Math.floorMod(a.y() - b.y(), height);
        return Math.min(dx, width - dx) <= 1 && Math.min(dy, height - dy) <= 1;
    }

    /** Each mark cluster, eight-connected across the tile's wrapped edges. */
    static List<Set<Texel>> clustersOf(Set<Texel> marks, int width, int height) {
        List<Set<Texel>> clusters = new ArrayList<>();
        Set<Texel> unvisited = new HashSet<>(marks);
        while (!unvisited.isEmpty()) {
            Texel seed = unvisited.iterator().next();
            Set<Texel> cluster = new HashSet<>();
            Deque<Texel> frontier = new ArrayDeque<>(List.of(seed));
            unvisited.remove(seed);
            while (!frontier.isEmpty()) {
                Texel texel = frontier.pop();
                cluster.add(texel);
                for (Texel other : List.copyOf(unvisited)) {
                    if (touchOnTheTile(texel, other, width, height)) {
                        unvisited.remove(other);
                        frontier.push(other);
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    /** The cluster's shape: its texels relative to its own corner, read on the wrapped tile from every texel in turn. */
    static Set<Set<Texel>> shapesOf(Set<Texel> cluster, int width, int height) {
        Set<Set<Texel>> shapes = new HashSet<>();
        for (Texel origin : cluster) {
            Set<Texel> shifted = cluster.stream()
                    .map(t -> new Texel(Math.floorMod(t.x() - origin.x(), width),
                            Math.floorMod(t.y() - origin.y(), height)))
                    .collect(Collectors.toSet());
            shapes.add(shifted);
        }
        return shapes;
    }

    @Test
    void anorthositeAndRegolithPlaceTheirMarksApart() throws IOException {
        List<String> shared = new ArrayList<>();
        for (String anorthositeSprite : LunarPaletteTest.variantsOf("anorthosite")) {
            BufferedImage anorthosite = gooBlock(anorthositeSprite);
            for (String regolithSprite : LunarPaletteTest.variantsOf("regolith")) {
                for (Texel a : darkMarksOf(anorthosite)) {
                    for (Texel r : darkMarksOf(regolithSprite)) {
                        if (touchOnTheTile(a, r, anorthosite.getWidth(), anorthosite.getHeight())) {
                            shared.add(anorthositeSprite + " " + a + " beside " + regolithSprite + " " + r);
                        }
                    }
                }
            }
        }
        assertTrue(shared.isEmpty(), "both blocks mark the same place: " + shared);
    }

    static Stream<String> groundSprites() {
        return LunarPaletteTest.GROUND_SPRITES.stream();
    }

    @ParameterizedTest
    @MethodSource("groundSprites")
    void noMarkClusterRepeatsInsideTheSprite(String sprite) throws IOException {
        List<String> repeats = repeatedClustersOf(gooBlock(sprite));
        assertTrue(repeats.isEmpty(), sprite + " repeats a mark: " + repeats);
    }

    @Test
    void twoDashesOfOneShapeReadAsARepeat() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                image.setRGB(x, y, 0xffbebfc1);
            }
        }
        for (Texel dark : List.of(new Texel(15, 2), new Texel(0, 3), new Texel(6, 10), new Texel(7, 11))) {
            image.setRGB(dark.x(), dark.y(), 0xff515151);
        }
        assertTrue(repeatedClustersOf(image).size() == 1, "the wrapped dash and its copy read as one repeat");
    }

    private static List<String> repeatedClustersOf(BufferedImage image) {
        List<Set<Texel>> clusters = clustersOf(darkMarksOf(image), image.getWidth(), image.getHeight()).stream()
                .filter(cluster -> cluster.size() > 1).toList();
        List<String> repeats = new ArrayList<>();
        for (int i = 0; i < clusters.size(); i++) {
            for (int j = i + 1; j < clusters.size(); j++) {
                Set<Set<Texel>> shapes = shapesOf(clusters.get(i), image.getWidth(), image.getHeight());
                if (shapes.removeAll(shapesOf(clusters.get(j), image.getWidth(), image.getHeight()))) {
                    repeats.add(clusters.get(i) + " repeats as " + clusters.get(j));
                }
            }
        }
        return repeats;
    }
}
