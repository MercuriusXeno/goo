package com.mercuriusxeno.goo.item;

import org.jspecify.annotations.Nullable;

/**
 * An item entity holding a fleck's cling, the one state the fleck's motion
 * carries from tick to tick.
 * decision flecks-move-by-flexible-goo-physics-and-stay-items
 */
public interface FleckHost {

    /**
     * @return the face the fleck clings to, or null for none
     */
    FleckCling.@Nullable Cling goo$cling();

    /**
     * @param cling the face the fleck clings to, or null to release it
     */
    void goo$setCling(FleckCling.@Nullable Cling cling);
}
