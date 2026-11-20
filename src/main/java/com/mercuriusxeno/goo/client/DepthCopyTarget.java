package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import org.jspecify.annotations.Nullable;

/**
 * The one builder of the targets a goo visual copies the world's depth
 * into: a copy's depth texture must hold its source's format, and a target
 * with a stencil holds a different depth format from one without, so the
 * target answered matches the source in size and in stencil, made on first
 * use and remade when either differs.
 * decision diagnose-and-correct-the-crash-in-every-depth-copying-visual
 */
public final class DepthCopyTarget {

    private final String label;
    private @Nullable TextureTarget target;

    /**
     * A builder whose targets carry a label.
     *
     * @param label the name its targets carry in a frame capture
     */
    public DepthCopyTarget(String label) {
        this.label = label;
    }

    /**
     * The target to copy the source's depth into, made or remade to match it.
     *
     * @param source the target whose depth is copied
     * @return a target the source's depth copies into
     */
    public TextureTarget matching(RenderTarget source) {
        Shape held = target == null ? null : Shape.of(target);
        Shape wanted = shapeToBuild(held, Shape.of(source));
        if (wanted != null) {
            if (target != null) {
                target.destroyBuffers();
            }
            target = new TextureTarget(label, wanted.width(), wanted.height(), true, wanted.stencil());
        }
        return target;
    }

    /**
     * The target last answered, or null before the first.
     *
     * @return the target last answered
     */
    public @Nullable TextureTarget current() {
        return target;
    }

    /**
     * What a depth texture's format follows: a target's size and whether it carries a stencil.
     *
     * @param width   its width in pixels
     * @param height  its height in pixels
     * @param stencil whether its depth texture carries a stencil
     */
    record Shape(int width, int height, boolean stencil) {

        static Shape of(RenderTarget target) {
            return new Shape(target.width, target.height, target.useStencil);
        }
    }

    /**
     * The shape of the target to build so a source's depth copies into it.
     *
     * @param held   the shape of the target held, null before the first
     * @param source the shape of the target copied from
     * @return the shape to build, or null where the held target already matches
     */
    static @Nullable Shape shapeToBuild(@Nullable Shape held, Shape source) {
        return source.equals(held) ? null : source;
    }
}
