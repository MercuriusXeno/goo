package com.mercuriusxeno.goo.client.ability;

/**
 * The window of a block's particle sprite a chunk of the block wears: its own
 * cell of the sprite, cut into a grid of equal cells, so the chunks standing
 * together show the block's texture.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 *
 * @param u0 the window's left edge in the atlas
 * @param u1 its right edge
 * @param v0 its top edge
 * @param v1 its bottom edge
 */
public record SpriteCrop(float u0, float u1, float v0, float v1) {

    /**
     * @param u0     the sprite's left edge in the atlas
     * @param u1     its right edge
     * @param v0     its top edge
     * @param v1     its bottom edge
     * @param across the cell's column, from the left
     * @param down   the cell's row, from the top
     * @param cells  cells along each edge of the sprite
     * @return the sprite's cell at that column and row
     */
    public static SpriteCrop cellOf(float u0, float u1, float v0, float v1, int across, int down, int cells) {
        float width = (u1 - u0) / cells;
        float height = (v1 - v0) / cells;
        return new SpriteCrop(u0 + width * across, u0 + width * (across + 1), v0 + height * down,
                v0 + height * (down + 1));
    }
}
