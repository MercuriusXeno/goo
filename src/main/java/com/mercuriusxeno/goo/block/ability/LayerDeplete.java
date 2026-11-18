package com.mercuriusxeno.goo.block.ability;

import java.util.function.DoubleSupplier;

/**
 * The roll a residual cell makes each time it acts on what is around it,
 * each block stage an Infest cell gnaws and each bite it lands, each mob a
 * Mortar cell petrifies a step and each block it hardens: a draw under the
 * chance takes one layer from the cell.
 * infest-has-a-chance-to-deplete-as-it-gnaws-and-bites
 * mortar-has-a-chance-to-deplete-as-it-petrifies-and-hardens
 */
public final class LayerDeplete {

    private LayerDeplete() {
    }

    /**
     * The layers a cell holds after one roll.
     *
     * @param layers the layers before the roll
     * @param draw   the roll's draw, 0 inclusive to 1 exclusive
     * @param chance the chance a roll takes a layer
     * @return one layer fewer where the draw falls under the chance, else the layers as they were
     */
    public static int layersAfterRoll(int layers, double draw, double chance) {
        return draw < chance ? Math.max(0, layers - 1) : layers;
    }

    /**
     * The layers a cell holds after a tick's rolls, one for each act the cell
     * made on what is around it.
     *
     * @param layers the layers before the rolls
     * @param rolls  the rolls the tick makes
     * @param draws  the source of each roll's draw, 0 inclusive to 1 exclusive
     * @param chance the chance a roll takes a layer
     * @return the layers left, 0 where the rolls took them all
     */
    public static int layersAfterRolls(int layers, int rolls, DoubleSupplier draws, double chance) {
        int left = layers;
        for (int roll = 0; roll < rolls && left > 0; roll++) {
            left = layersAfterRoll(left, draws.getAsDouble(), chance);
        }
        return left;
    }
}
