package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.jspecify.annotations.Nullable;

/**
 * The offscreen buffers the Unmake drink's spheres splat into before the
 * shade pass draws them as one skin: the fluid buffer holds each sphere's
 * surface depth, over a copy of the level's depth so walls hide the goo, and
 * its lit color with its radius as coverage, clear where no sphere covers;
 * the blur buffer, the same size, holds the first blur pass's result while
 * the second writes back into the fluid buffer.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
final class DrinkFluidTarget {

    private static final String FLUID_LABEL = "Goo unmake drink fluid";
    private static final String BLUR_LABEL = "Goo unmake drink blur";

    private static @Nullable RenderTarget fluid;
    private static @Nullable RenderTarget blur;

    private DrinkFluidTarget() {
    }

    /**
     * @param main the main render target
     * @return the fluid buffer at the main target's size, made or resized to it
     */
    static RenderTarget fluidSizedTo(RenderTarget main) {
        if (fluid == null) {
            fluid = new TextureTarget(FLUID_LABEL, main.width, main.height, true);
        }
        fit(fluid, main);
        return fluid;
    }

    /**
     * @param main the main render target
     * @return the blur buffer at the main target's size, made or resized to it
     */
    static RenderTarget blurSizedTo(RenderTarget main) {
        if (blur == null) {
            blur = new TextureTarget(BLUR_LABEL, main.width, main.height, true);
        }
        fit(blur, main);
        return blur;
    }

    private static void fit(RenderTarget target, RenderTarget main) {
        if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height);
        }
    }
}
