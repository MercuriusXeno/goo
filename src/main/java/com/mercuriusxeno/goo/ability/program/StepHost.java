package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.Set;

/**
 * The seam a step program reaches its world through (decision
 * host-agnostic-runtime). The runtime holds a host, never a level or a
 * block entity, so the same program runs on the marker block, the struck
 * entity or the potion holder. Reads answer through {@link Variables} by
 * name and through the typed accessors; world actions are host methods,
 * so a test drives a program against a mock with no level behind it.
 *
 * <p>The surface here is what the standing steps need. Each host
 * implementation states which variables it binds; a step reading a name
 * its host leaves unbound reads zero with a warning until the load-time
 * refusal lands with the entity host.
 */
public interface StepHost extends Variables {

    /**
     * Returns the block the program acts from: the marker block, or the
     * block the struck entity stands in.
     *
     * @return the anchor position
     */
    BlockPos position();

    /**
     * Returns the face the marker was placed on; the blast direction is
     * its opposite.
     *
     * @return the placed face
     */
    Direction placedFace();

    /**
     * Returns the blobs stacked on the host, live.
     *
     * @return the stack count
     */
    int stackCount();

    /**
     * Spends one stacked blob.
     */
    void decrementStack();

    /**
     * Detonates at the anchor's center.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    void explode(float power, ExplosionMode mode);

    /**
     * Scans the volume around the anchor for an entity every filter keeps.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @return true when at least one entity is in the volume
     */
    boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters);
}
