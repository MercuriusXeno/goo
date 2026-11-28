package com.mercuriusxeno.goo.block.ability;

/**
 * What a residual cell spends as it acts on what is around it: one layer for
 * each cost, each block stage an Infest cell gnaws and each bite it lands.
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 */
public final class LayerDeplete {

    private LayerDeplete() {
    }

    /**
     * The layers a cell holds after a tick's costs, one layer each.
     *
     * @param layers the layers before
     * @param costs  the costs the tick ran up
     * @return the layers left, 0 where the costs took them all
     */
    public static int layersAfterCosts(int layers, int costs) {
        return Math.max(0, layers - costs);
    }
}
