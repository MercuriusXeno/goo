package com.mercuriusxeno.goo.client;

import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * How a translucent goo visual on the {@link TranslucentVisualPath} sorts
 * against what the level has drawn, named by the visual as it registers, and
 * the level render stage the path draws it at. The path draws a stage's
 * routes in the order they are declared here.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
 */
public enum SortRoute {

    /**
     * A decal lying flush on an opaque block face, drawn right after the
     * opaque blocks: the face's own depth already stands, so every
     * translucent thing drawn later blends over it where nearer and fails
     * against the face where farther.
     */
    ON_OPAQUE_FACES(RenderLevelStageEvent.AfterOpaqueBlocks.class),

    /**
     * Drawn regardless of depth, under a standing decision that shows it
     * through blocks, such as the x-ray overlays and the held ghost's second
     * pass (decision held-visual-ghosts-the-landing-in-two-passes). Drawn
     * before the far-to-near visuals, since what it shows stands behind the
     * blocks every one of them stands in front of.
     */
    THROUGH_BLOCKS(RenderLevelStageEvent.AfterLevel.class),

    /**
     * Drawn once the level is drawn, clouds, weather and the Fabulous blend
     * included, tested against the level's depth, after every other goo
     * visual farther from the camera and before every nearer one. A visual
     * behind water or glass stays hidden on Fast and Fancy and untinted on
     * Fabulous until order-independent transparency lands on 26.3 (decision
     * goo-translucency-draws-through-order-independent-transparency).
     */
    FAR_TO_NEAR(RenderLevelStageEvent.AfterLevel.class),

    /**
     * A post effect over the finished frame, such as Flames' heat shimmer:
     * it holds no depth to sort, so the path only hands it the frame.
     */
    SCREEN_SPACE(RenderLevelStageEvent.AfterLevel.class);

    private final Class<? extends RenderLevelStageEvent> stage;

    SortRoute(Class<? extends RenderLevelStageEvent> stage) {
        this.stage = stage;
    }

    /**
     * The level render stage the path draws this route's visuals at.
     *
     * @return the stage's event class
     */
    public Class<? extends RenderLevelStageEvent> stage() {
        return stage;
    }
}
