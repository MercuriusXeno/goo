package com.mercuriusxeno.goo.client.radial;

/**
 * Where a wheel's petals draw on screen: its center and its outer radius, which the petal painter scales each
 * petal's unit shape by, so the glove's wheel and the sprayer's paint their petals alike.
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 */
interface PetalCanvas {

    /**
     * @return the wheel's center, x on screen
     */
    int centerX();

    /**
     * @return the wheel's center, y on screen
     */
    int centerY();

    /**
     * @return the wheel's outer radius in pixels
     */
    int radius();
}
