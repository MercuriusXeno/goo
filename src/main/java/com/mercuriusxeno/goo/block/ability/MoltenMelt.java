package com.mercuriusxeno.goo.block.ability;

import java.util.List;

/**
 * How many layers a Molten cell spends melting or cooking the blocks it
 * touches: one apiece, for each block that melts under the molten step's
 * melts_into map or cooks, as many as its layers pay for; none otherwise, so
 * the goo stays pooled.
 * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
 * molten-heats-every-block-its-cell-touches
 */
public final class MoltenMelt {

    /** The layers melting one block spends. */
    public static final int LAYERS_PER_BLOCK = 1;

    private MoltenMelt() {
    }

    /**
     * The blocks a cell's layers pay to melt or cook, of those it heats.
     * molten-heats-every-block-its-cell-touches
     *
     * @param layers the layers the cell holds
     * @param heated the blocks it heats that melt or cook, in the order they are paid for
     * @param <T>    what names a heated block
     * @return the first of them its layers pay for
     */
    public static <T> List<T> paidFor(int layers, List<T> heated) {
        return heated.subList(0, Math.min(heated.size(), layers / LAYERS_PER_BLOCK));
    }
}
