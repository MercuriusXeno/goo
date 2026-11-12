package com.mercuriusxeno.goo.client.ability;

/**
 * petrify_stone.fsh's stone coordinate on the CPU, the texture coordinate it
 * samples the stone at before the wrap, so a test reads how far one skin texel
 * moves the stone sample.
 */
final class PetrifyStoneModel {

    /** The expression petrify_stone.fsh samples the stone at, as the model mirrors it. */
    static final String STONE_SAMPLE =
            "texture(Sampler0, fract(skinCoord * skinTexels / vec2(textureSize(Sampler0, 0)) + stoneColor.rg))";

    private PetrifyStoneModel() {
    }

    /**
     * The stone coordinate at a skin coordinate, before the wrap and the seed's shift.
     *
     * @param skinU      the skin coordinate's u, 0 to 1
     * @param skinV      the skin coordinate's v, 0 to 1
     * @param skinWidth  the skin's texels across
     * @param skinHeight the skin's texels down
     * @param stoneSize  the stone texture's texels on a side
     * @return the stone coordinate, {u, v}
     */
    static float[] stoneCoord(float skinU, float skinV, int skinWidth, int skinHeight, int stoneSize) {
        return new float[] {skinU * skinWidth / stoneSize, skinV * skinHeight / stoneSize};
    }
}
