package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;

/**
 * A block entity a marker host stands at: the ability block a lingering
 * ability stands, or the prism a combo runs on. The host reads the face,
 * the goo type and the ability from it, and keeps its program's state in it.
 * decision prism-hosts-the-combos
 */
public interface MarkerAnchor {

    /**
     * @return the face the anchor stands on
     */
    Direction getPlacedFace();

    /**
     * @return the goo type the anchor's program runs as
     */
    ResourceKey<GooTypeDefinition> getGooType();

    /**
     * @return the id of the ability whose program the anchor runs
     */
    String getAbilityId();

    /**
     * @return the state the anchor's program keeps between ticks
     */
    MarkerProgramState programState();

    /**
     * Banks one tick of standing on an anchor that stores ticks; an anchor
     * that stores none ignores it.
     * timekeeper-prism-banks-ticks-forward-only
     *
     * @param perTick  the charge standing banks a tick
     * @param spending the most charge one held tick of Tick spends
     */
    default void bankTicks(int perTick, int spending) {
    }
}
