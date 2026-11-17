package com.mercuriusxeno.goo.block.ability;

/**
 * How many layers a Molten cell spends melting the block below it: three, a
 * fifth of a block, where the cell holds at least three and the block melts
 * into something under the molten step's melts_into map; none otherwise, so
 * the goo stays pooled.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * molten-spends-three-layers-per-stone-block-priced-at-lava-minus-mundane-block
 */
public final class MoltenMelt {

    /** The layers melting one block spends, a fifth of a block of molten goo. */
    public static final int LAYERS_PER_BLOCK = 3;

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
