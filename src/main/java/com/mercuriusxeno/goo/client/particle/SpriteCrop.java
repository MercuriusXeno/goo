package com.mercuriusxeno.goo.client.particle;

import net.minecraft.util.RandomSource;

/**
 * The window of a block's particle sprite a break particle wears: a random
 * quarter of it across and down, as vanilla's break particles crop theirs.
 * decision unmake-drink-is-a-stream-of-break-particles
 *
 * @param u0 the window's left edge in the atlas
 * @param u1 its right edge
 * @param v0 its top edge
 * @param v1 its bottom edge
 */
public record SpriteCrop(float u0, float u1, float v0, float v1) {

    /** The crop's share of the sprite across and down. */
    private static final float PART = 0.25f;

    /**
     * @param u0     the sprite's left edge in the atlas
     * @param u1     its right edge
     * @param v0     its top edge
     * @param v1     its bottom edge
     * @param random the particle's random
     * @return a random quarter of the sprite
     */
    public static SpriteCrop within(float u0, float u1, float v0, float v1, RandomSource random) {
        float across = random.nextFloat() * (1 - PART);
        float down = random.nextFloat() * (1 - PART);
        float width = u1 - u0;
        float height = v1 - v0;
        return new SpriteCrop(u0 + width * across, u0 + width * (across + PART), v0 + height * down,
                v0 + height * (down + PART));
    }
}
