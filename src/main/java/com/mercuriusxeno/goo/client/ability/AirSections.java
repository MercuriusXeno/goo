package com.mercuriusxeno.goo.client.ability;

/**
 * Which sixteen-block sections of the world hold only air, so a field's
 * walk passes over every block of one without reading it: air carries no
 * wall, so the walls the walk finds are the same.
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
@FunctionalInterface
interface AirSections {

    /** A world read block by block, no section taken as air alone. */
    AirSections NONE = (sectionX, sectionY, sectionZ) -> false;

    /**
     * @param sectionX the section's x, in sections
     * @param sectionY the section's y, in sections
     * @param sectionZ the section's z, in sections
     * @return true only where every block of the section reads as air
     */
    boolean allAir(int sectionX, int sectionY, int sectionZ);
}
