package com.mercuriusxeno.goo.tools;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The committed soft glow beam texture, read through the classpath, is the
 * one GlowBeamTextureGenerator renders.
 * decision every-glow-beam-feathers-to-transparent
 */
class GlowBeamTextureRegeneratedTest {

    private static final String TEXTURE = "/assets/goo/textures/entity/glow_beam.png";

    @Test
    void theCommittedTextureIsTheGeneratorsRender() throws IOException {
        BufferedImage committed;
        try (InputStream in = GlowBeamTextureRegeneratedTest.class.getResourceAsStream(TEXTURE)) {
            assertNotNull(in, "missing on the classpath: " + TEXTURE);
            committed = ImageIO.read(in);
        }
        BufferedImage rendered = GlowBeamTextureGenerator.render();
        assertEquals(rendered.getWidth(), committed.getWidth());
        assertEquals(rendered.getHeight(), committed.getHeight());
        for (int y = 0; y < rendered.getHeight(); y++) {
            for (int x = 0; x < rendered.getWidth(); x++) {
                assertEquals(Integer.toHexString(rendered.getRGB(x, y)), Integer.toHexString(committed.getRGB(x, y)),
                        "the committed texture differs from the generator's render at " + x + "," + y);
            }
        }
    }
}
