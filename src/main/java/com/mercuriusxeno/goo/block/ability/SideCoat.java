package com.mercuriusxeno.goo.block.ability;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

/**
 * What a volume cell holds against one of its four sides' walls, beside the
 * fill on its floor.
 * goo-cell-holds-a-fill-and-a-state-per-side
 * world-goo-fills-a-block-space-in-one-to-fifteen-layers
 */
public enum SideCoat implements StringRepresentable {

    /** Nothing against that wall. */
    BARE("bare"),
    /** The block's goo attached to that wall, clinging to it. */
    ATTACHED("attached"),
    /** One layer running down that wall. */
    RUNNY("runny");

    private final String serialized;

    SideCoat(String serialized) {
        this.serialized = serialized;
    }

    /**
     * @return true where the side holds a layer, a film or a runny one
     */
    public boolean holdsALayer() {
        return this != BARE;
    }

    @Override
    public @NonNull String getSerializedName() {
        return serialized;
    }
}
