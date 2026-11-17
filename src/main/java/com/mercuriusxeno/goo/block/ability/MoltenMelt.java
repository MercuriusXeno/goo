package com.mercuriusxeno.goo.block.ability;

/**
 * How many layers a Molten cell spends melting the block below it: one,
 * where the block melts into something under the molten step's melts_into
 * map; none otherwise, so the goo stays pooled.
 * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
 */
public final class MoltenMelt {

    /** The layers melting one block spends. */
    public static final int LAYERS_PER_BLOCK = 1;

    private MoltenMelt() {
    }

    /**
     * @param layers the layers the cell holds
     * @param melts  whether the block under the cell melts into anything
     * @return the layers the melt spends, 0 where nothing melts
     */
    public static int layersSpent(int layers, boolean melts) {
        return layers >= LAYERS_PER_BLOCK && melts ? LAYERS_PER_BLOCK : 0;
    }
}
