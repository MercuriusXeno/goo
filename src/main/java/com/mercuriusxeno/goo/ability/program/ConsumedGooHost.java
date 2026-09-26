package com.mercuriusxeno.goo.ability.program;

/**
 * A host filling a goo total from the valued blocks around its anchor and
 * dropping it as blobs (capability {@link HostCapability#CONSUMED_GOO}).
 */
public interface ConsumedGooHost extends StepHost {

    /**
     * Removes every valued block within a sphere around the anchor, adding
     * its goo to the total the host keeps.
     *
     * @param radius the sphere radius in whole blocks
     */
    void consumeValuedBlocks(int radius);

    /**
     * Drops the consumed goo total as blob items at the anchor and empties it.
     */
    void dropConsumedGoo();
}
