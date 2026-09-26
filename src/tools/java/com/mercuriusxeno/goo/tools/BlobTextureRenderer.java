package com.mercuriusxeno.goo.tools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Renders blob sprite variants (tiny, small, base, large) from cellular automata heat frames
 * using luminance masks for shading.
 */
final class BlobTextureRenderer {

    /**
     * Seed offset for blob CA to decorrelate from fluid CA.
     */
    static final long BLOB_SEED_OFFSET = 7919L;
    /**
     * Extra warmup ticks for blob generation beyond base warmup.
     */
    static final int BLOB_EXTRA_WARMUP = 20;

    /**
     * File suffix for blob variant PNG textures.
     */
    static final String SUFFIX_BLOB_TINY = "_blob_tiny";
    /**
     * File suffix for small blob variant.
     */
    static final String SUFFIX_BLOB_SMALL = "_blob_small";
    /**
     * File suffix for base blob variant.
     */
    static final String SUFFIX_BLOB_BASE = "_blob_base";
    /**
     * File suffix for large blob variant.
     */
    static final String SUFFIX_BLOB_LARGE = "_blob_large";
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

    private BlobTextureRenderer() {
    }

    /**
     * Generates tiny, small, base, and large blob sprites for a goo type using luminance masks.
     *
     * @param type the goo fluid type definition
     * @throws IOException if texture or mask files cannot be read or written
     */
    static void generateBlobBase(FluidTextureGenerator.GooFluidType type) throws IOException {
        FluidTextureGenerator.FluidCA ca = new FluidTextureGenerator.FluidCA(
                type.genParams(), type.seed() + BLOB_SEED_OFFSET);
        FluidTextureGenerator.warmup(ca, FluidTextureGenerator.WARMUP + BLOB_EXTRA_WARMUP);
        float[][] heatFrames = FluidTextureGenerator.captureHeatFrames(ca);
        float[] heatRange = FluidTextureGenerator.findHeatRange(heatFrames);
        writeBlobVariants(type, heatFrames, heatRange);
    }

    /**
     * Loads all size masks and writes masked blob sprite variants.
     *
     * @param type       the goo fluid type definition
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @throws IOException if texture or mask files cannot be read or written
     */
    static void writeBlobVariants(FluidTextureGenerator.GooFluidType type, float[][] heatFrames,
                                  float... heatRange) throws IOException {
        float[][] tinyMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.BLOB_MASK_TINY_PATH);
        float[][] smallMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.BLOB_MASK_SMALL_PATH);
        float[][] baseMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.BLOB_MASK_PATH);
        float[][] largeMask = TextureColorShader.loadLuminanceMask(FluidTextureGenerator.BLOB_MASK_LARGE_PATH);
        writeMaskedBlobVariant(heatFrames, heatRange, type, tinyMask, type.id() + SUFFIX_BLOB_TINY);
        writeMaskedBlobVariant(heatFrames, heatRange, type, smallMask, type.id() + SUFFIX_BLOB_SMALL);
        writeMaskedBlobVariant(heatFrames, heatRange, type, baseMask, type.id() + SUFFIX_BLOB_BASE);
        writeMaskedBlobVariant(heatFrames, heatRange, type, largeMask, type.id() + SUFFIX_BLOB_LARGE);
    }

    /**
     * Writes a masked blob variant (luminance-shaded, single layer) with sprite strip + mcmeta.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param type       the goo fluid type definition
     * @param mask       the luminance mask for shading
     * @param baseName   the output filename without extension
     * @throws IOException if texture files cannot be written
     */
    static void writeMaskedBlobVariant(float[][] heatFrames, float[] heatRange,
                                       FluidTextureGenerator.GooFluidType type, float[][] mask, String baseName) throws IOException {
        BufferedImage strip = renderBlobStripMasked(heatFrames, heatRange, type, mask);
        ImageIO.write(strip, FORMAT_PNG, FluidTextureGenerator.ITEM_DIR.resolve(baseName + EXT_PNG).toFile());
        TextureMcmetaWriter.writeBlobMcmeta(type.frametime(), FluidTextureGenerator.ITEM_DIR, baseName + EXT_PNG_MCMETA);
    }

    /**
     * Renders a blob sprite strip with luminance mask modulating the palette colors.
     *
     * @param heatFrames the raw heat values per frame
     * @param heatRange  min and range values for normalization
     * @param type       the goo fluid type definition
     * @param mask       the luminance mask for shading
     * @return the rendered blob sprite strip image
     */
    static BufferedImage renderBlobStripMasked(
            float[][] heatFrames, float[] heatRange, FluidTextureGenerator.GooFluidType type, float[]... mask) {
        BufferedImage strip = new BufferedImage(
                FluidTextureGenerator.SIZE, FluidTextureGenerator.SIZE * FluidTextureGenerator.FRAMES,
                BufferedImage.TYPE_INT_ARGB);
        for (int frame = 0; frame < FluidTextureGenerator.FRAMES; frame++) {
            renderMaskedFrame(strip, heatFrames[frame], frame, heatRange[0], heatRange[1], type, mask);
        }
        return strip;
    }

    /**
     * Renders a single masked blob frame into the composite strip.
     *
     * @param strip   the composite sprite strip image
     * @param heat    the raw heat values for this frame
     * @param frame   the frame index
     * @param minHeat the minimum heat for normalization
     * @param range   the heat range for normalization
     * @param type    the goo fluid type definition
     * @param mask    the luminance mask
     */
    static void renderMaskedFrame(BufferedImage strip, float[] heat, int frame,
                                  float minHeat, float range, FluidTextureGenerator.GooFluidType type, float[]... mask) {
        for (int y = 0; y < FluidTextureGenerator.SIZE; y++) {
            for (int x = 0; x < FluidTextureGenerator.SIZE; x++) {
                int py = frame * FluidTextureGenerator.SIZE + y;
                strip.setRGB(x, py, maskedPixel(heat, mask, x, y, minHeat, range, type));
            }
        }
    }

    /**
     * Computes a single masked pixel color, returning transparent if outside the mask.
     *
     * @param heat    the raw heat values for the current frame
     * @param mask    the luminance mask (0 = transparent, >0 = visible)
     * @param x       the pixel X coordinate
     * @param y       the pixel Y coordinate
     * @param minHeat the minimum heat value for normalization
     * @param range   the heat range for normalization
     * @param type    the goo fluid type definition providing palette and shadow hue
     * @return the ARGB pixel color, or transparent if outside the mask
     */
    private static int maskedPixel(float[] heat, float[][] mask, int x, int y,
                                   float minHeat, float range, FluidTextureGenerator.GooFluidType type) {
        float lum = mask[y][x];
        if (lum <= 0) {
            return FluidTextureGenerator.TRANSPARENT;
        }
        float normalized = FluidTextureGenerator.normalizeHeat(
                heat[y * FluidTextureGenerator.SIZE + x], minHeat, range);
        return TextureColorShader.modulateColorShaded(
                type.palette().sample(normalized), lum, type.shadowHue());
    }
}
