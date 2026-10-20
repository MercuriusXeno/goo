package com.mercuriusxeno.goo.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Renders goo sprite variants (tiny, small, base, large) from cellular automata heat frames
 * using luminance masks for shading.
 */
final class GooTextureRenderer {

    /**
     * Seed offset for goo CA to decorrelate from fluid CA.
     */
    static final long GOO_SEED_OFFSET = 7919L;
    /**
     * Extra warmup ticks for goo generation beyond base warmup.
     */
    static final int GOO_EXTRA_WARMUP = 20;

    /**
     * File suffix for goo variant PNG textures.
     */
    static final String SUFFIX_GOO_TINY = "_goo_tiny";
    /**
     * File suffix for small goo variant.
     */
    static final String SUFFIX_GOO_SMALL = "_goo_small";
    /**
     * File suffix for base goo variant.
     */
    static final String SUFFIX_GOO_BASE = "_goo_base";
    /**
     * File suffix for large goo variant.
     */
    static final String SUFFIX_GOO_LARGE = "_goo_large";
    /**
     * File extension for PNG files.
     */
    static final String EXT_PNG = ".png";
    /**
     * File extension suffix for mcmeta sidecar files.
     */
    static final String EXT_PNG_MCMETA = ".png.mcmeta";
    /**
     * Image format for PNG output.
     */
    private static final String FORMAT_PNG = "PNG";

    private GooTextureRenderer() {
    }

    /**
     * Generates tiny, small, base, and large goo sprites for a goo type using luminance masks.
     *
     * @param type the goo fluid type definition
     * @throws IOException if texture or mask files cannot be read or written
     */
    static void generateGooBase(FluidTextureGenerator.GooFluidType type) throws IOException {
        writeGooVariants(type, maskedHeat(type));
    }

    /**
     * Runs a goo type's goo CA and captures the heat its goo sprites sample.
     *
     * @param type the goo fluid type definition
     * @return the base heat, its range and the accent bands
     */
    static MaskedHeat maskedHeat(FluidTextureGenerator.GooFluidType type) {
        FluidTextureGenerator.FluidCA ca = new FluidTextureGenerator.FluidCA(
                type.genParams(), type.seed() + GOO_SEED_OFFSET);
        FluidTextureGenerator.warmup(ca, FluidTextureGenerator.WARMUP + GOO_EXTRA_WARMUP);
        float[][] heatFrames = FluidTextureGenerator.captureHeatFrames(ca);
        return new MaskedHeat(heatFrames, FluidTextureGenerator.findHeatRange(heatFrames), AccentLayer.capture(type));
    }

    /**
     * Loads all size masks and writes masked goo sprite variants.
     *
     * @param type the goo fluid type definition
     * @param heat the base heat, its range and the accent bands
     * @throws IOException if texture or mask files cannot be read or written
     */
    static void writeGooVariants(FluidTextureGenerator.GooFluidType type, MaskedHeat heat) throws IOException {
        float[][] tinyMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.GOO_MASK_TINY_PATH);
        float[][] smallMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.GOO_MASK_SMALL_PATH);
        float[][] baseMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.GOO_MASK_PATH);
        float[][] largeMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.GOO_MASK_LARGE_PATH);
        writeMaskedGooVariant(heat, type, tinyMask, type.id() + SUFFIX_GOO_TINY);
        writeMaskedGooVariant(heat, type, smallMask, type.id() + SUFFIX_GOO_SMALL);
        writeMaskedGooVariant(heat, type, baseMask, type.id() + SUFFIX_GOO_BASE);
        writeMaskedGooVariant(heat, type, largeMask, type.id() + SUFFIX_GOO_LARGE);
    }

    /**
     * The per-frame fields a goo sprite samples: the base heat, its range, and the accent bands.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param bands      per frame, each cell's accent band position
     */
    record MaskedHeat(float[][] heatFrames, float[] heatRange, float[][] bands) {
    }

    /**
     * Writes a masked goo variant (luminance-shaded, single layer) with sprite strip + mcmeta.
     *
     * @param heat     the base heat, its range and the accent bands
     * @param type     the goo fluid type definition
     * @param mask     the luminance mask for shading
     * @param baseName the output filename without extension
     * @throws IOException if texture files cannot be written
     */
    static void writeMaskedGooVariant(MaskedHeat heat, FluidTextureGenerator.GooFluidType type, float[][] mask,
                                      String baseName) throws IOException {
        BufferedImage strip = renderGooStripMasked(heat, type, mask);
        ImageIO.write(strip, FORMAT_PNG, FluidTextureGenerator.ITEM_DIR.resolve(baseName + EXT_PNG).toFile());
        TextureMcmetaWriter.writeGooMcmeta(type.frametime(), FluidTextureGenerator.ITEM_DIR, baseName + EXT_PNG_MCMETA);
    }

    /**
     * Renders a goo sprite strip with luminance mask modulating the palette colors.
     *
     * @param heat the base heat, its range and the accent bands
     * @param type the goo fluid type definition
     * @param mask the luminance mask for shading
     * @return the rendered goo sprite strip image
     */
    static BufferedImage renderGooStripMasked(MaskedHeat heat, FluidTextureGenerator.GooFluidType type,
                                              float[]... mask) {
        BufferedImage strip = new BufferedImage(
                FluidTextureGenerator.SIZE, FluidTextureGenerator.SIZE * FluidTextureGenerator.FRAMES,
                BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            renderMaskedFrame(strip, heat, frame, type, mask);
        }
        return strip;
    }

    /**
     * Renders a single masked goo frame into the composite strip.
     *
     * @param strip the composite sprite strip image
     * @param heat  the base heat, its range and the accent bands
     * @param frame the frame index
     * @param type  the goo fluid type definition
     * @param mask  the luminance mask
     */
    static void renderMaskedFrame(BufferedImage strip, MaskedHeat heat, int frame,
                                  FluidTextureGenerator.GooFluidType type, float[]... mask) {
        for (int y = 0; y < FluidTextureGenerator.SIZE; y++) {
            for (int x = 0; x < FluidTextureGenerator.SIZE; x++) {
                int py = frame * FluidTextureGenerator.SIZE + y;
                strip.setRGB(x, py, maskedPixel(heat, frame, mask[y][x], y * FluidTextureGenerator.SIZE + x, type));
            }
        }
    }

    /**
     * Computes a single masked pixel color, returning transparent if outside the mask.
     *
     * @param heat  the base heat, its range and the accent bands
     * @param frame the frame index
     * @param lum   the mask's luminance at the pixel (0 = transparent, >0 = visible)
     * @param cell  the pixel's index in the frame
     * @param type  the goo fluid type definition providing palettes and shadow hue
     * @return the ARGB pixel color, or transparent if outside the mask
     */
    private static int maskedPixel(MaskedHeat heat, int frame, float lum, int cell,
                                   FluidTextureGenerator.GooFluidType type) {
        if (lum <= 0) {
            return FluidTextureGenerator.TRANSPARENT;
        }
        float normalized = FluidTextureGenerator.normalizeHeat(
                heat.heatFrames()[frame][cell], heat.heatRange()[0], heat.heatRange()[1]);
        int color = AccentLayer.sampleColor(type, normalized, heat.bands()[frame][cell]);
        return TextureColorShader.modulateColorShaded(color, lum, type.shadowHue());
    }
}
