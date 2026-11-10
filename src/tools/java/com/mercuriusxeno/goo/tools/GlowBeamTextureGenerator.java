package com.mercuriusxeno.goo.tools;

import net.minecraft.util.ARGB;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Writes glow's soft beam texture: its alpha rises from nothing at both side
 * columns to full at mid-width along a sine, so a beam face drawn across the
 * texture's whole width feathers to transparent at its corners and its
 * silhouette shows no stair-step; a soft band of brightness runs along v, so
 * the beam's scroll still reads.
 * decision every-glow-beam-feathers-to-transparent
 * <p>
 * Run through {@code ./gradlew generateGlowBeamTexture}; it writes
 * src/main/resources/assets/goo/textures/entity/glow_beam.png.
 */
@SuppressWarnings("PMD.SystemPrintln") // standalone CLI tool; no logger needed
public final class GlowBeamTextureGenerator {

    /** Texture columns across the beam's width. */
    static final int WIDTH = 32;
    /** Texture rows along the beam, one scrolling band. */
    static final int HEIGHT = 64;
    private static final Path OUTPUT = Path.of("src/main/resources/assets/goo/textures/entity/glow_beam.png");
    private static final int OPAQUE = 255;
    /** The share of full brightness the band dims to at its darkest row. */
    private static final double BAND_DEPTH = 0.3;
    private static final double HALF = 0.5;

    private GlowBeamTextureGenerator() {
    }

    /**
     * Writes the texture.
     *
     * @param args unused
     * @throws IOException when the file cannot be written
     */
    public static void main(String[] args) throws IOException {
        Files.createDirectories(OUTPUT.getParent());
        ImageIO.write(render(), "png", OUTPUT.toFile());
        System.out.println("Wrote " + OUTPUT);
    }

    /**
     * Renders the texture.
     *
     * @return the soft beam texture, {@link #WIDTH} by {@link #HEIGHT}
     */
    static BufferedImage render() {
        BufferedImage texture = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            int gray = (int) Math.round(OPAQUE * (1 - BAND_DEPTH * (1 - Math.cos(2 * Math.PI * y / HEIGHT)) * HALF));
            for (int x = 0; x < WIDTH; x++) {
                int alpha = (int) Math.round(OPAQUE * Math.sin(Math.PI * x / (WIDTH - 1)));
                texture.setRGB(x, y, ARGB.color(alpha, gray, gray, gray));
            }
        }
        return texture;
    }
}
