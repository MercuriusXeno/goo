package com.mercuriusxeno.goo.client;

/**
 * How a translucent pipeline GooRenderTypes builds sorts against the level,
 * recorded beside the pipeline so a new one states its sorting as it is
 * written; GooRenderTypesTest fails a translucent pipeline recording none.
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
 */
public enum PipelineSort {

    /** Drawn by a visual registered with {@link TranslucentVisualPath}, under its {@link SortRoute}. */
    ON_THE_PATH,

    /** Writes its own depth, so whatever draws later and farther, the clouds among it, stands behind it. */
    WRITES_DEPTH,

    /** Passes every depth test under a named decision, showing through blocks. */
    THROUGH_BLOCKS,

    /**
     * Lies flush on a surface that writes its own depth, such as an overlay on
     * a mob's model, so whatever draws later and farther fails against that surface.
     */
    FLUSH_ON_A_DEPTH_WRITING_SURFACE,

    /** Drawn in vanilla's sky pass, behind everything the level draws after it. */
    WITH_THE_SKY
}
