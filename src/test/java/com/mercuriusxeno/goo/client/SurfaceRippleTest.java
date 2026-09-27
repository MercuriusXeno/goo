package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java ripple port follows the wave goo_fluid_surface.vsh draws: the same constants,
 * read from the shader through the class loader, and the same day fraction vanilla feeds
 * its GameTime uniform (decision each-tile-bobs-with-the-ripple).
 */
class SurfaceRippleTest {

    private static final String SHADER = "/assets/goo/shaders/core/goo_fluid_surface.vsh";
    private static final float EPSILON = 1e-6f;

    private static float shaderConstant(String source, String name) {
        Matcher matcher = Pattern.compile("const float " + name + " = ([0-9.]+);").matcher(source);
        assertTrue(matcher.find(), name + " missing from " + SHADER);
        return Float.parseFloat(matcher.group(1));
    }

    /** Each cycle count and wavenumber the port declares equals the shader's. */
    @Test
    void portConstantsMatchTheShader() throws IOException {
        String source;
        try (InputStream stream = SurfaceRippleTest.class.getResourceAsStream(SHADER)) {
            assertNotNull(stream, SHADER);
            source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertEquals(shaderConstant(source, "PRIMARY_CYCLES_PER_DAY"), SurfaceRipple.PRIMARY_CYCLES_PER_DAY);
        assertEquals(shaderConstant(source, "SECONDARY_CYCLES_PER_DAY"), SurfaceRipple.SECONDARY_CYCLES_PER_DAY);
        assertEquals(shaderConstant(source, "PRIMARY_WAVENUMBER"), SurfaceRipple.PRIMARY_WAVENUMBER);
        assertEquals(shaderConstant(source, "SECONDARY_WAVENUMBER"), SurfaceRipple.SECONDARY_WAVENUMBER);
        assertTrue(source.contains("vec2(" + SurfaceRipple.PRIMARY_DIRECTION_X + ", "
                + SurfaceRipple.PRIMARY_DIRECTION_Z + ")"), "the primary direction differs from the shader's");
    }

    /** At a primary crest with the secondary wave at zero, the wave reads half its peak. */
    @Test
    void waveSumsTheTwoSinesAtHalfWeight() {
        double crest = Math.PI / 2 / SurfaceRipple.PRIMARY_WAVENUMBER / SurfaceRipple.PRIMARY_DIRECTION_X;

        assertEquals(0.5f + 0.5f * (float) Math.sin(-0.5 * crest * SurfaceRipple.SECONDARY_WAVENUMBER),
                SurfaceRipple.at(crest, 0, 0f), EPSILON);
    }

    /** The day fraction wraps each 24000 ticks and adds the partial tick, as vanilla's GameTime does. */
    @Test
    void dayFractionWrapsTheDay() {
        assertEquals(0.5f / 24_000f, SurfaceRipple.dayFraction(24_000L, 0.5f), EPSILON);
        assertEquals(12_000f / 24_000f, SurfaceRipple.dayFraction(36_000L, 0f), EPSILON);
    }
}
