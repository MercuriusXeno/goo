package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.jspecify.annotations.Nullable;

/**
 * The offscreen buffer the Unmake drink's spheres splat into before the shade
 * pass draws them as one skin: each sphere's surface depth in its depth
 * texture, over a copy of the level's depth so walls hide the goo, and its
 * lit color in its color texture, clear where no sphere covers.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
final class DrinkFluidTarget {

    /** The buffer's label in GPU debuggers. */
    private static final String LABEL = "Goo unmake drink fluid";

    private static @Nullable RenderTarget target;

    private DrinkFluidTarget() {
    }

    /**
     * The fluid buffer at the main target's size, made or resized to it.
     *
     * @param main the main render target
     * @return the fluid buffer
     */
    static RenderTarget sizedTo(RenderTarget main) {
        if (target == null) {
            target = new TextureTarget(LABEL, main.width, main.height, true);
        } else if (target.width != main.width || target.height != main.height) {
            target.resize(main.width, main.height);
        }
        return target;
    }
}
