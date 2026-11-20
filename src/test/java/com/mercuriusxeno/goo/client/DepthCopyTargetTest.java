package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers the depth copy builder's decision of what target to build for a source,
 * one target held for each stencil format, read from the sources' sizes and stencils without a GPU.
 */
class DepthCopyTargetTest {

    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;
    private static final int SMALLER_WIDTH = 1280;
    private static final int SMALLER_HEIGHT = 720;

    @Test
    void sourceWithAStencilIsAnsweredATargetWithAStencil() {
        DepthCopyTarget.Shape built = new DepthCopyTarget.HeldShapes().buildFor(shapeOf(sourceOf(true)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, true), built);
    }

    @Test
    void sourceWithoutAStencilIsAnsweredATargetWithoutOne() {
        DepthCopyTarget.Shape built = new DepthCopyTarget.HeldShapes().buildFor(shapeOf(sourceOf(false)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, false), built);
    }

    @Test
    void sourceAlternatingStencilAtOneSizeBuildsEachFormatOnce() {
        DepthCopyTarget.HeldShapes held = new DepthCopyTarget.HeldShapes();

        DepthCopyTarget.Shape withStencil = held.buildFor(shapeOf(sourceOf(true)));
        DepthCopyTarget.Shape withoutStencil = held.buildFor(shapeOf(sourceOf(false)));
        DepthCopyTarget.Shape withStencilAgain = held.buildFor(shapeOf(sourceOf(true)));
        DepthCopyTarget.Shape withoutStencilAgain = held.buildFor(shapeOf(sourceOf(false)));

        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, true), withStencil);
        assertEquals(new DepthCopyTarget.Shape(WIDTH, HEIGHT, false), withoutStencil);
        assertNull(withStencilAgain);
        assertNull(withoutStencilAgain);
    }

    /** A source changing size remakes the target held for its own format and leaves the other's standing. */
    @Nested
    class SizeChangeWithinOneFormat {

        @Test
        void sourceWithAStencilChangingSizeRemakesItsTarget() {
            assertSizeChangeRemakesOnly(true);
        }

        @Test
        void sourceWithoutAStencilChangingSizeRemakesItsTarget() {
            assertSizeChangeRemakesOnly(false);
        }

        private void assertSizeChangeRemakesOnly(boolean stencil) {
            DepthCopyTarget.HeldShapes held = new DepthCopyTarget.HeldShapes();
            held.buildFor(shapeOf(sourceOf(!stencil)));
            held.buildFor(shapeOf(sourceOf(stencil)));

            DepthCopyTarget.Shape resized = held.buildFor(
                    shapeOf(sourceOf(stencil, SMALLER_WIDTH, SMALLER_HEIGHT)));
            DepthCopyTarget.Shape otherFormat = held.buildFor(shapeOf(sourceOf(!stencil)));

            assertEquals(new DepthCopyTarget.Shape(SMALLER_WIDTH, SMALLER_HEIGHT, stencil), resized);
            assertNull(otherFormat);
        }
    }

    private static DepthCopyTarget.Shape shapeOf(RenderTarget source) {
        return DepthCopyTarget.Shape.of(source);
    }

    private static RenderTarget sourceOf(boolean stencil) {
        return sourceOf(stencil, WIDTH, HEIGHT);
    }

    private static RenderTarget sourceOf(boolean stencil, int width, int height) {
        RenderTarget source = new RenderTarget("source", true, stencil) {
        };
        source.width = width;
        source.height = height;
        return source;
    }
}
