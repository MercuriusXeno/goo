package com.mercuriusxeno.goo.tools;

import net.minecraft.util.ARGB;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/**
 * Luminance-based color shading for fluid and blob textures.
 * Shadows shift toward a per-type hue with saturation boost;
 * highlights blend toward white for specular sheen.
 */
final class TextureColorShader {

    /**
     * Luminance above this threshold produces specular sheen.
     */
    private static final float HIGHLIGHT_THRESHOLD = 0.8f;
    /**
     * How aggressively shadow hue rotates toward the per-type shadow hue.
     */
    private static final float HUE_SHIFT_STRENGTH = 0.3f;
    /**
     * Saturation boost applied in shadow regions to counteract desaturation.
     */
    private static final float SAT_BOOST = 0.2f;
    /**
     * Maximum specular sheen blend factor.
     */
    private static final float MAX_SHEEN_BLEND = 0.5f;

    /**
     * Maximum channel value (white).
     */
    private static final int MAX_CHANNEL = 255;

    /**
     * Index for hue/red in HSB/RGBA arrays.
     */
    private static final int IDX_R = 0;
    /**
     * Index for saturation/green in HSB/RGBA arrays.
     */
    private static final int IDX_G = 1;
    /**
     * Index for brightness/blue in HSB/RGBA arrays.
     */
    private static final int IDX_B = 2;

    /**
     * Texture size in pixels.
     */
    private static final int SIZE = 16;
    /**
     * Red weight for luminance calculation (Rec. 601).
     */
    private static final float LUMA_RED_WEIGHT = 0.299f;
    /**
     * Green weight for luminance calculation.
     */
    private static final float LUMA_GREEN_WEIGHT = 0.587f;
    /**
     * Blue weight for luminance calculation.
     */
    private static final float LUMA_BLUE_WEIGHT = 0.114f;
    /**
     * Channel normalizer (divides 0-255 to 0.0-1.0).
     */
    private static final float CHANNEL_NORMALIZER = 255.0f;

    private TextureColorShader() {
    }

    /**
     * Two-zone luminance modulation: below the highlight threshold, darkens
     * via HSB with optional hue shift; above, blends toward white.
     *
     * @param argb      the source ARGB color
     * @param luminance the mask luminance value (0.0 to 1.0)
     * @param shadowHue the per-type shadow hue for dark regions
     * @return the modulated ARGB color
     */
    static int modulateColorShaded(int argb, float luminance, float shadowHue) {
        if (luminance >= HIGHLIGHT_THRESHOLD) {
            float sheenFactor = (luminance - HIGHLIGHT_THRESHOLD) / (1.0f - HIGHLIGHT_THRESHOLD);
            return applyHighlight(argb, sheenFactor);
        }
        float normalizedLum = luminance / HIGHLIGHT_THRESHOLD;
        return applyShadow(argb, normalizedLum, shadowHue);
    }

    /**
     * Darkens the color via HSB. Negative shadowHue disables hue shift and saturation boost.
     *
     * @param argb          the source ARGB color
     * @param normalizedLum the luminance normalized to highlight threshold
     * @param shadowHue     the target shadow hue, or negative to disable
     * @return the darkened ARGB color
     */
    private static int applyShadow(int argb, float normalizedLum, float shadowHue) {
        int a = ARGB.alpha(argb);
        float[] hsb = Color.RGBtoHSB(
                ARGB.red(argb), ARGB.green(argb), ARGB.blue(argb), null);
        shiftShadowHsb(hsb, normalizedLum, shadowHue);
        int rgb = Color.HSBtoRGB(hsb[IDX_R], hsb[IDX_G], hsb[IDX_B]);
        return ARGB.color(a, rgb);
    }

    /**
     * Applies shadow hue shift, saturation boost, and brightness reduction in HSB space.
     *
     * @param hsb           the HSB array to modify in place
     * @param normalizedLum the luminance normalized to highlight threshold
     * @param shadowHue     the target shadow hue, or negative to disable
     */
    private static void shiftShadowHsb(float[] hsb, float normalizedLum, float shadowHue) {
        float darkness = 1.0f - normalizedLum;
        if (shadowHue >= 0) {
            hsb[IDX_R] = lerpFloat(hsb[IDX_R], shadowHue, darkness * HUE_SHIFT_STRENGTH);
            hsb[IDX_G] = Math.min(1.0f, hsb[IDX_G] + darkness * SAT_BOOST);
        }
        hsb[IDX_B] = hsb[IDX_B] * normalizedLum;
    }

    /**
     * Lerps the palette color toward white by a sheen factor (capped at 0.5 blend).
     *
     * @param argb        the source ARGB color
     * @param sheenFactor the specular sheen intensity (0.0 to 1.0)
     * @return the highlighted ARGB color
     */
    private static int applyHighlight(int argb, float sheenFactor) {
        int a = ARGB.alpha(argb);
        float t = sheenFactor * MAX_SHEEN_BLEND;
        int r = (int) lerpFloat(ARGB.red(argb), MAX_CHANNEL, t);
        int g = (int) lerpFloat(ARGB.green(argb), MAX_CHANNEL, t);
        int b = (int) lerpFloat(ARGB.blue(argb), MAX_CHANNEL, t);
        return ARGB.color(a, r, g, b);
    }

    /**
     * Linear interpolation between two floats.
     *
     * @param from the start value
     * @param to   the end value
     * @param t    the interpolation factor (0.0 to 1.0)
     * @return the interpolated value
     */
    static float lerpFloat(float from, float to, float t) {
        return from + (to - from) * t;
    }

    /**
     * Loads a PNG mask and extracts per-pixel luminance (0.0-1.0), incorporating alpha.
     *
     * @param maskPath the path to the PNG mask file
     * @return 2D luminance array indexed by [y][x]
     * @throws IOException if the mask file cannot be read
     */
    static float[][] loadLuminanceMask(Path maskPath) throws IOException {
        BufferedImage mask = ImageIO.read(maskPath.toFile());
        float[][] luminance = new float[SIZE][SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                luminance[y][x] = extractLuminance(mask.getRGB(x, y));
            }
        }
        return luminance;
    }

    /**
     * Extracts luminance from an ARGB pixel, scaled by alpha. Transparent pixels return 0.
     *
     * @param argb the ARGB pixel value
     * @return luminance in the range 0.0 to 1.0
     */
    private static float extractLuminance(int argb) {
        int a = ARGB.alpha(argb);
        if (a == 0) {
            return 0.0f;
        }
        int r = ARGB.red(argb);
        int g = ARGB.green(argb);
        int b = ARGB.blue(argb);
        float lum = (LUMA_RED_WEIGHT * r + LUMA_GREEN_WEIGHT * g + LUMA_BLUE_WEIGHT * b) / CHANNEL_NORMALIZER;
        return lum * (a / CHANNEL_NORMALIZER);
    }
}
