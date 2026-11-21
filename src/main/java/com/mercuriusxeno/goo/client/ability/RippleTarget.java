package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.DepthCopyTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.jspecify.annotations.Nullable;

/**
 * The offscreen buffer an afterimage's ripple draws its silhouette masks
 * into before the edge pass reads their perimeter: one color channel per
 * silhouette, and a copy of the world's depth so walls hide the masks.
 * Decision afterimage-is-one-shared-effect.
 */
public final class RippleTarget {

    /** The buffer's label in GPU debuggers. */
    private static final String LABEL = "Goo Ripple";

    private static final DepthCopyTarget TARGET = new DepthCopyTarget(LABEL);

    private RippleTarget() {
    }

    /**
     * The target the ripple's mask render types draw into.
     *
     * @return the ripple buffer, or null before the first ripple makes it
     */
    public static @Nullable RenderTarget current() {
        return TARGET.current();
    }

    /**
     * The ripple buffer, made or remade to match the main target so the
     * main target's depth copies into it.
     * decision diagnose-and-correct-the-crash-in-every-depth-copying-visual
     *
     * @param main the main render target
     * @return the ripple buffer
     */
    static RenderTarget matching(RenderTarget main) {
        return TARGET.matching(main);
    }
}
