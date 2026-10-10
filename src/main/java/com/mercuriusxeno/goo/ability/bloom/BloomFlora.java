package com.mercuriusxeno.goo.ability.bloom;

/**
 * The flora a surface takes from Bloom, a random plant of which it spawns:
 * a lily pad on water, a vine on a wall, cave flora in a cave, and one of
 * the biome's own plants in a field.
 * bloom-places-buds-by-biome-and-surface
 */
public enum BloomFlora {
    /** Still water, taking a lily pad. */
    WATER,
    /** A wall under the open sky, taking a vine. */
    WALL,
    /** A cave's floor, ceiling or wall, taking moss, glow lichen or a spore blossom. */
    CAVE,
    /** Open plantable ground, taking one of the biome's own plants. */
    FIELD
}
