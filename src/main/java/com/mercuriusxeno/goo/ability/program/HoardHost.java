package com.mercuriusxeno.goo.ability.program;

/**
 * A host filling a hoard of stacks from the blocks and items around its
 * anchor and leaving it as a compression sphere (capability
 * {@link HostCapability#HOARD}).
 */
public interface HoardHost extends StepHost {

    /**
     * Takes every breakable block within a sphere around the anchor into the
     * hoard as its silk-touched drops, removing it.
     *
     * @param radius the sphere radius in whole blocks
     */
    void hoardBlocks(int radius);

    /**
     * Pulls the item entities within a sphere toward the anchor, taking each
     * one that reaches the anchor into the hoard.
     *
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center, in blocks per tick
     */
    void pullItemsIntoHoard(double radius, double speed);

    /**
     * Leaves the hoard as one compression sphere at the anchor and empties it.
     */
    void dropSphere();
}
