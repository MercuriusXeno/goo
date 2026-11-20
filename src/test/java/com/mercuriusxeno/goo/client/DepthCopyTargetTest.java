package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers the depth copy builder's decision of what target to build for a source,
 * read from the source's size and stencil without a GPU.
 */
class DepthCopyTargetTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    @Test
    void sourceWithAStencilIsAnsweredATargetWithAStencil() {
        DepthCopyTarget.Shape built = DepthCopyTarget.shapeToBuild(null, shapeOf(sourceOf(true)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, true), built);
    }

    @Test
    void sourceWithoutAStencilIsAnsweredATargetWithoutOne() {
        DepthCopyTarget.Shape built = DepthCopyTarget.shapeToBuild(null, shapeOf(sourceOf(false)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, false), built);
    }

    @Test
    void sourceOfOneSizeChangingStencilRemakesTheTarget() {
        DepthCopyTarget.Shape first = DepthCopyTarget.shapeToBuild(null, shapeOf(sourceOf(false)));
        DepthCopyTarget.Shape remade = DepthCopyTarget.shapeToBuild(first, shapeOf(sourceOf(true)));
        DepthCopyTarget.Shape back = DepthCopyTarget.shapeToBuild(remade, shapeOf(sourceOf(false)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, true), remade);
        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, false), back);
    }

    @Test
    void sourceMatchingTheHeldTargetKeepsIt() {
        DepthCopyTarget.Shape held = DepthCopyTarget.shapeToBuild(null, shapeOf(sourceOf(true)));

        assertEquals(null, DepthCopyTarget.shapeToBuild(held, shapeOf(sourceOf(true))));
    }

    private static DepthCopyTarget.Shape shapeOf(RenderTarget source) {
        return DepthCopyTarget.Shape.of(source);
    }

    private static RenderTarget sourceOf(boolean stencil) {
        RenderTarget source = new RenderTarget("source", true, stencil) {
        };
        source.width = WIDTH;
        source.height = HEIGHT;
        return source;
    }
}
