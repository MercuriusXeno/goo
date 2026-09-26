package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import java.util.HashMap;
import java.util.Map;

/**
 * Generates and caches anti-aliased mask textures for the radial wheel:
 * one white-on-transparent DynamicTexture per wedge (a type wedge in either
 * ring, or an ability wedge of a fan), shaped as a {@link PetalMask}, and
 * per hub circle. Edges are
 * smoothed via 4x4 sub-pixel multi-sampling. A mask spans the wheel's full
 * diameter, so every mask blits over the same square.
 */
public final class RadialTextures {
    /**
     * Texture resolution of every mask; the renderer scales it to the wheel's diameter.
     */
    static final int TEX_SIZE = 256;

    /**
     * Sub-samples per axis for anti-aliasing (4x4 = 16 samples per pixel).
     */
    private static final int AA_SAMPLES = 4;

    private static final double TWO_PI = 2.0 * Math.PI;
    private static final double HALF_DIVISOR = 2.0;
    private static final double SAMPLE_CENTER = 0.5;
    private static final int MAX_ALPHA = 255;

    /**
     * Resolution a mask's angles and radii are keyed at: parameters equal
     * to this many parts share one texture.
     */
    private static final double KEY_SCALE = 10_000.0;

    private static final String ARC_LABEL = "goo_radial_arc_";
    private static final String ARC_PATH = "dynamic/radial_arc_";
    private static final String HUB_LABEL = "goo_radial_hub_";
    private static final String HUB_PATH = "dynamic/radial_hub_";
    private static final String KEY_SEPARATOR = "_";

    private static final Map<String, Identifier> MASKS = new HashMap<>();

    private RadialTextures() {
    }

    /**
     * Returns the mask of a wedge's petal, generating it on first use.
     *
     * @param startAngle the arc's start, clockwise from the top, in radians
     * @param arc        the arc's span in radians
     * @param innerNorm  the band's inner radius as a fraction of the wheel's
     * @param outerNorm  the band's outer radius as a fraction of the wheel's
     * @return the registered texture identifier
     */
    public static Identifier getArcTexture(double startAngle, double arc, double innerNorm, double outerNorm) {
        double start = wrap(startAngle);
        String key = keyOf(start) + KEY_SEPARATOR + keyOf(arc) + KEY_SEPARATOR
                + keyOf(innerNorm) + KEY_SEPARATOR + keyOf(outerNorm);
        return MASKS.computeIfAbsent(ARC_PATH + key, path -> register(path, ARC_LABEL + key,
                (x, y) -> PetalMask.isInsidePetal(x, y, start, arc, innerNorm, outerNorm)));
    }

    /**
     * Returns the mask of a filled circle at the wheel's center, generating it on first use.
     *
     * @param radiusNorm the circle's radius as a fraction of the wheel's
     * @return the registered texture identifier
     */
    public static Identifier getHubTexture(double radiusNorm) {
        String key = keyOf(radiusNorm);
        return MASKS.computeIfAbsent(HUB_PATH + key, path -> register(path, HUB_LABEL + key,
                (x, y) -> Math.sqrt(x * x + y * y) <= radiusNorm));
    }

    private static String keyOf(double value) {
        return Long.toString(Math.round(value * KEY_SCALE));
    }

    private static double wrap(double angle) {
        double wrapped = angle % TWO_PI;
        return wrapped < 0 ? wrapped + TWO_PI : wrapped;
    }

    private static Identifier register(String path, String label, MaskShape shape) {
        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, true);
        rasterize(image, shape);
        Identifier id = Identifier.fromNamespaceAndPath(Goo.MODID, path);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> label, image));
        return id;
    }

    private static void rasterize(NativeImage image, MaskShape shape) {
        double half = TEX_SIZE / HALF_DIVISOR;
        for (int py = 0; py < TEX_SIZE; py++) {
            for (int px = 0; px < TEX_SIZE; px++) {
                writePixelIfHit(image, px, py, countHits(px, py, half, shape));
            }
        }
    }

    private static int countHits(int px, int py, double half, MaskShape shape) {
        int hits = 0;
        for (int sy = 0; sy < AA_SAMPLES; sy++) {
            for (int sx = 0; sx < AA_SAMPLES; sx++) {
                if (shape.contains(toNormalized(px, sx, half), toNormalized(py, sy, half))) {
                    hits++;
                }
            }
        }
        return hits;
    }

    private static double toNormalized(int pixel, int sample, double half) {
        return (pixel + (sample + SAMPLE_CENTER) / AA_SAMPLES - half) / half;
    }

    private static void writePixelIfHit(NativeImage image, int px, int py, int hits) {
        if (hits > 0) {
            int alpha = hits * MAX_ALPHA / (AA_SAMPLES * AA_SAMPLES);
            image.setPixel(px, py, ARGB.color(alpha, MAX_ALPHA, MAX_ALPHA, MAX_ALPHA));
        }
    }

    /** A mask's shape over normalized coordinates. */
    @FunctionalInterface
    private interface MaskShape {
        boolean contains(double x, double y);
    }
}
