package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The portal's pixel rim is cut by the swirl band's line: it paints only
 * outside that line, no farther than a texel and a half out, stepping texel
 * by texel, strongest by the line (decision vortex-mouth-cuts-the-pixel-rim).
 * The rim draws in portal_texels.glsl, read here through the classpath; the
 * strength checks mirror its portalTexelRim arithmetic on its own named
 * constants.
 */
class PortalRimTest {

    private static final String PORTAL_TEXELS = "/assets/goo/shaders/core/portal_texels.glsl";
    private static final double TEXELS_PER_BLOCK = 16;
    /** How far apart two numbers may stand and still read as one, float rounding only. */
    private static final double NEAR = 1.0e-6;

    @Test
    void theRimIsCutOnTheSwirlBandsLine() throws IOException {
        assertEquals(PortalFaces.BAND_INSET * TEXELS_PER_BLOCK, constant("PORTAL_RIM_LINE"), NEAR,
                "The rim's cut should run on the swirl band's line, PortalFaces.BAND_INSET in texels");
    }

    @Test
    void theRimPaintsNothingInsideTheLine() throws IOException {
        double line = constant("PORTAL_RIM_LINE");
        for (double exact : new double[] {line + 0.01, line + 0.5, line + 3}) {
            assertEquals(0, rim(line + 0.2, exact), NEAR, "A point inside the line should take no rim, at " + exact);
        }
    }

    @Test
    void theRimPaintsNothingMoreThanATexelAndAHalfOutside() throws IOException {
        double line = constant("PORTAL_RIM_LINE");
        assertEquals(1.5, constant("PORTAL_RIM_WIDTH"), NEAR, "The rim should reach a texel and a half at most");
        assertEquals(0, rim(line - 1.2, line - 1.6), NEAR,
                "A point more than a texel and a half outside the line should take no rim");
    }

    @Test
    void theRimStepsFainterTexelByTexelOutward() throws IOException {
        double line = constant("PORTAL_RIM_LINE");
        double previous = Double.MAX_VALUE;
        for (double outward = 0; outward < 1.5; outward += 0.25) {
            double strength = rim(line - outward, line - outward);
            assertTrue(strength <= previous + NEAR, "The rim should grow no stronger outward, at " + outward);
            previous = strength;
        }
        assertTrue(rim(line + 0.3, line - 0.05) > 0, "A texel the line cuts should paint its part outside the line");
    }

    /** The rim's strength, mirroring portalTexelRim on portal_texels.glsl's own constants. */
    private static double rim(double texelInside, double exactInside) throws IOException {
        double line = constant("PORTAL_RIM_LINE");
        double width = constant("PORTAL_RIM_WIDTH");
        if (exactInside > line || line - exactInside > width) {
            return 0;
        }
        double outward = Math.max(line - texelInside, 0);
        return constant("PORTAL_RIM_STRENGTH") * Math.max(1 - outward / width, 0);
    }

    private static double constant(String name) throws IOException {
        Matcher match = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(source());
        assertTrue(match.find(), "portal_texels.glsl should name " + name);
        return Double.parseDouble(match.group(1));
    }

    private static String source() throws IOException {
        try (InputStream in = PortalRimTest.class.getResourceAsStream(PORTAL_TEXELS)) {
            assertTrue(in != null, "portal_texels.glsl should be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
