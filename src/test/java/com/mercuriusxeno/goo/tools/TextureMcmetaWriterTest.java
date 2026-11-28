package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Covers the sidecar text the texture generator writes, against the sidecars the mod ships.
 */
class TextureMcmetaWriterTest {

    private static final String SHIPPED_FLUID_MCMETA = "/assets/goo/textures/fluid/astral_fluid.png.mcmeta";

    @Test
    void fluidSidecarMatchesTheShippedPingPongSidecar() throws IOException {
        int astralFrametime = DarkGooResources.fluidType("astral").frametime();
        assertEquals(shipped(SHIPPED_FLUID_MCMETA), TextureMcmetaWriter.fluidMcmeta(astralFrametime).strip());
    }

    private static String shipped(String path) throws IOException {
        try (InputStream in = TextureMcmetaWriterTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
