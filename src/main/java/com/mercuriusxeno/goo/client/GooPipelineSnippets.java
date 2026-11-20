package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * The pipeline snippets goo builds its own pipelines on where the game
 * keeps none: Minecraft 26.2 folded its matrices-and-projection snippet
 * into its bind group layouts, so goo's shaders reading the two blocks
 * alone take them here.
 * decision minecraft-26-3-update-is-a-port-to-parity
 */
public final class GooPipelineSnippets {

    /** The DynamicTransforms and Projection uniform blocks, as 26.1's matrices-and-projection snippet held. */
    public static final RenderPipeline.Snippet MATRICES_PROJECTION_SNIPPET = RenderPipeline.builder()
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .buildSnippet();

    private GooPipelineSnippets() {
    }
}
