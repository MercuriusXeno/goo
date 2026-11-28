package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swirl band draws full at its inner edge, flush on the portal's face,
 * and fades only as it moves away from the face onto the wall, so its far
 * side, lifted off the face, leaves no gap between it and the face
 * (decision diagnose-and-correct-the-swirl-bands-depth-under-improved-transparency).
 * Shaders are read through the classpath.
 */
class SwirlBandShaderTest {

    private static final String BAND = "/assets/goo/shaders/core/goo_portal_swirl_band.fsh";

    private static String band() throws IOException {
        try (InputStream in = SwirlBandShaderTest.class.getResourceAsStream(BAND)) {
            assertNotNull(in, BAND);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    @Test
    void theBandFadesOnlyAwayFromTheFace() throws IOException {
        String band = band();

        assertTrue(band.contains("float feather = pow(1.0 - outward, BAND_GHOST);"),
                "the band's glow is full from its inner edge to the rim and thins only outward");
        assertFalse(band.contains("smoothstep(0.0, BAND_FEATHER, across)"),
                "the band's inner edge, flush on the face, fades in from nothing");
    }
}
