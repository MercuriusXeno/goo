package com.mercuriusxeno.goo.tools;

import net.minecraft.util.ARGB;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Writes glow's beam texture in the look of a Minecraft beacon beam: sixteen
 * pixels wide with streaks of brightness running along it, solid across the
 * middle and fading to nothing over the outermost columns, so a beam strip
 * drawn across the texture's whole width keeps a crisp body while its
 * outline feathers instead of stair-stepping. The softer whole-width fade it
 * replaced no longer read as a Minecraft beam (operator UAT 2026-11-10).
 * decision every-glow-beam-feathers-to-transparent
 * <p>
 * Run through {@code ./gradlew generateGlowBeamTexture}; it writes
 * src/main/resources/assets/goo/textures/entity/glow_beam.png.
 */
@SuppressWarnings("PMD.SystemPrintln") // standalone CLI tool; no logger needed
public final class GlowBeamTextureGenerator {

    /** Texture columns across the beam's width, a beacon beam's sixteen. */
    static final int WIDTH = 16;
    /** Texture rows along the beam. */
    static final int HEIGHT = 64;
    private static final Path OUTPUT = Path.of("src/main/resources/assets/goo/textures/entity/glow_beam.png");
    private static final int OPAQUE = 255;
    /** Columns on each side the alpha climbs over, from nothing at the side to solid. */
    private static final int FEATHER_COLUMNS = 3;
    /** The darkest a streak runs, out of 255. */
    private static final int DARKEST_STREAK = 190;
    /** Rows one streak segment holds its brightness before the next. */
    private static final int ROWS_PER_SEGMENT = 4;
    /** A fixed seed, so the texture regenerates the same each run. */
    private static final long SEED = 0x9100BEAAL;

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
     * @return the beam texture, {@link #WIDTH} by {@link #HEIGHT}
     */
    static BufferedImage render() {
        BufferedImage texture = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Random streaks = new Random(SEED);
        for (int x = 0; x < WIDTH; x++) {
            int alpha = alphaAt(x);
            int gray = OPAQUE;
            for (int y = 0; y < HEIGHT; y++) {
                if (y % ROWS_PER_SEGMENT == 0) {
                    gray = DARKEST_STREAK + streaks.nextInt(OPAQUE - DARKEST_STREAK + 1);
                }
                texture.setRGB(x, y, ARGB.color(alpha, gray, gray, gray));
            }
        }
        return texture;
    }

    /**
     * A column's alpha: nothing at either side column, climbing over
     * {@link #FEATHER_COLUMNS} columns to solid across the middle.
     *
     * @param x the column
     * @return the alpha, 0 to 255
     */
    static int alphaAt(int x) {
        int fromTheSide = Math.min(x, WIDTH - 1 - x);
        return fromTheSide >= FEATHER_COLUMNS ? OPAQUE : OPAQUE * fromTheSide / FEATHER_COLUMNS;
    }
}
