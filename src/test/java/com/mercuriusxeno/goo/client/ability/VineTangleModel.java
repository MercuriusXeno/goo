package com.mercuriusxeno.goo.client.ability;

/**
 * vine_tangle.fsh's vine tile coordinate on the CPU, the texture coordinate it
 * samples the vine at before the wrap, so a test reads how far one skin texel
 * moves the vine sample.
 */
final class VineTangleModel {

    /** The expression vine_tangle.fsh samples the vine at, as the model mirrors it. */
    static final String VINE_SAMPLE =
            "texture(Sampler0, fract(skinCoord * skinTexels / vec2(textureSize(Sampler0, 0))))";

    private VineTangleModel() {
    }

    /**
     * The vine coordinate at a skin coordinate, before the wrap.
     *
     * @param skinU      the skin coordinate's u, 0 to 1
     * @param skinV      the skin coordinate's v, 0 to 1
     * @param skinWidth  the skin's texels across
     * @param skinHeight the skin's texels down
     * @param vineSize   the vine texture's texels on a side
     * @return the vine coordinate, {u, v}
     */
    static float[] vineCoord(float skinU, float skinV, int skinWidth, int skinHeight, int vineSize) {
        return new float[] {skinU * skinWidth / vineSize, skinV * skinHeight / vineSize};
    }
}
