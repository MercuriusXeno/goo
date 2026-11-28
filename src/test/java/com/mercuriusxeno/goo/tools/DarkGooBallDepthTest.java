package com.mercuriusxeno.goo.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that nether's, void's, sculk's and astral's committed goo_base strips span at least 80 percent of the HSL
 * lightness span Quantum's goo_base strip spans over its opaque pixels.
 */
class DarkGooBallDepthTest {

    private static final List<String> DARK_TYPES = DarkGooResources.DARK_TYPES;
    private static final float SHARE_OF_QUANTUMS_SPAN = 0.8f;

    // dark-goos-climb-to-a-second-hue
    @ParameterizedTest
    @FieldSource("DARK_TYPES")
    void gooBallSpansQuantumsDepth(String type) throws IOException {
        float quantumSpan = lightnessSpan(DarkGooResources.texture("item/quantum_goo_base.png"));
        float span = lightnessSpan(DarkGooResources.texture("item/" + type + "_goo_base.png"));
        assertTrue(span >= SHARE_OF_QUANTUMS_SPAN * quantumSpan,
                type + "'s goo ball spans lightness " + span + " against quantum's " + quantumSpan);
    }

    private static float lightnessSpan(BufferedImage strip) {
        float darkest = 1f;
        float brightest = 0f;
        for (int y = 0; y < strip.getHeight(); y++) {
            for (int x = 0; x < strip.getWidth(); x++) {
                int argb = strip.getRGB(x, y);
                if ((argb >>> 24) == 0) {
                    continue;
                }
                float lightness = DarkGooResources.hsl(argb & 0xFFFFFF)[2];
                darkest = Math.min(darkest, lightness);
                brightest = Math.max(brightest, lightness);
            }
        }
        return brightest - darkest;
    }
}
