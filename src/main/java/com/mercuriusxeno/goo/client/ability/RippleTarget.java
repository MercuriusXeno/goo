package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import org.jspecify.annotations.Nullable;

/**
 * The offscreen buffer an afterimage's ripple draws its silhouette masks
 * into before the edge pass reads their perimeter: one color channel per
 * silhouette, and a copy of the world's depth so walls hide the masks.
 * Decision afterimage-is-one-shared-effect.
 */
public final class RippleTarget {

    /** The output the ripple's mask render types draw into. */
    public static final OutputTarget OUTPUT = new OutputTarget("goo_ripple", RippleTarget::current);

    /** The buffer's label in GPU debuggers. */
    private static final String LABEL = "Goo Ripple";

    private static @Nullable RenderTarget target;

    private RippleTarget() {
    }

    private static @Nullable RenderTarget current() {
        return target;
    }

    /**
     * The ripple buffer at the main target's size, made or resized to it.
     *
     * @param main the main render target
     * @return the ripple buffer
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
