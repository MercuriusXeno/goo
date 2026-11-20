package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;

/**
 * A pipeline's color blend worked by hand: the color a fragment leaves on the target, as the GPU blends it from the
 * fragment's color and the color already there. Covers the factors goo's sky pipelines name.
 */
public final class BlendComposite {

    private static final int CHANNELS = 3;

    private BlendComposite() {
    }

    /**
     * The color a fragment leaves where it draws over a color already on the target.
     *
     * @param pipeline the pipeline the fragment draws through
     * @param source   the fragment's red, green, blue and alpha, 0 to 1
     * @param target   the target's red, green and blue, 0 to 1
     * @return the target's red, green and blue after the draw
     */
    public static float[] over(RenderPipeline pipeline, float[] source, float[] target) {
        BlendFunction blend = pipeline.getColorTargetState().blendFunction().orElse(null);
        if (blend == null) {
            return new float[] {source[0], source[1], source[2]};
        }
        float alpha = source[CHANNELS];
        float sourceFactor = factor(blend.color().sourceFactor(), alpha);
        float destFactor = factor(blend.color().destFactor(), alpha);
        float[] shown = new float[CHANNELS];
        for (int channel = 0; channel < CHANNELS; channel++) {
            shown[channel] = Math.min(1F, source[channel] * sourceFactor + target[channel] * destFactor);
        }
        return shown;
    }

    private static float factor(BlendFactor factor, float alpha) {
        return switch (factor) {
            case ONE -> 1F;
            case ZERO -> 0F;
            case SRC_ALPHA -> alpha;
            case ONE_MINUS_SRC_ALPHA -> 1F - alpha;
            default -> throw new IllegalArgumentException("unworked blend factor " + factor);
        };
    }
}
