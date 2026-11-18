package com.mercuriusxeno.goo.client;

/**
 * How a translucent goo visual on the {@link TranslucentVisualPath} sorts
 * against what the level has drawn, named by the visual as it registers.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
public enum SortRoute {

    /**
     * Drawn once the level is drawn, clouds, weather and the Fabulous blend
     * included, tested against the level's depth, after every other goo visual
     * farther from the camera and before every nearer one.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    FAR_TO_NEAR,

    /**
     * Drawn regardless of depth, under a standing decision that shows it
     * through blocks, such as the x-ray overlays and the held ghost's second
     * pass (decision held-visual-ghosts-the-landing-in-two-passes). Drawn
     * before the far-to-near visuals, since what it shows stands behind the
     * blocks every one of them stands in front of.
     */
    THROUGH_BLOCKS
}
